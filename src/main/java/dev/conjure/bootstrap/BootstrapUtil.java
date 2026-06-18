package dev.conjure.bootstrap;

import dev.conjure.Conjure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Low-level helpers shared by the backend bootstrappers: HTTP downloads with coarse progress
 * logging, archive extraction (.zip via the JDK, .7z via the Windows-bundled {@code tar}), process
 * launching, and reachability checks. All output is routed to the Conjure log; nothing here blocks
 * the game thread (callers run on the {@code conjure-bootstrap} daemon thread).
 */
final class BootstrapUtil {

    /** Follows redirects because GitHub releases and Hugging Face both 302 to a CDN. */
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private BootstrapUtil() {}

    /** True if an HTTP GET to {@code endpoint} returns any status (i.e. something is listening). */
    static boolean httpReachable(String endpoint) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<Void> r = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            return r.statusCode() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Polls {@link #httpReachable} once a second up to {@code seconds}. */
    static boolean waitReachable(String endpoint, int seconds) {
        for (int i = 0; i < seconds; i++) {
            if (httpReachable(endpoint)) return true;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Downloads {@code url} to {@code dest} (skips if already present and non-empty). */
    static void download(String url, Path dest, String label) throws IOException, InterruptedException {
        if (Files.exists(dest) && Files.size(dest) > 0) {
            Conjure.LOGGER.info("[bootstrap] {} already downloaded ({}).", label, dest.getFileName());
            return;
        }
        Files.createDirectories(dest.getParent());
        Path part = dest.resolveSibling(dest.getFileName() + ".part");

        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException(label + ": HTTP " + resp.statusCode() + " for " + url);
        }
        long total = resp.headers().firstValueAsLong("content-length").orElse(-1);
        Conjure.LOGGER.info("[bootstrap] downloading {} ({})...", label, human(total));

        try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buf = new byte[1 << 20];
            long done = 0, lastLog = 0;
            int r;
            while ((r = in.read(buf)) >= 0) {
                out.write(buf, 0, r);
                done += r;
                if (done - lastLog >= 100L * 1024 * 1024) { // log every ~100 MB
                    lastLog = done;
                    Conjure.LOGGER.info("[bootstrap]   {} {} / {}", label, human(done), human(total));
                }
            }
        }
        Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
        Conjure.LOGGER.info("[bootstrap] {} download complete.", label);
    }

    /** Extracts a .zip using the JDK (guards against zip-slip). */
    static void unzip(Path zip, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = destDir.resolve(e.getName()).normalize();
                if (!out.startsWith(destDir)) {
                    throw new IOException("Refusing zip entry outside target: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Extracts a .7z by shelling out to {@code tar} (libarchive), which is bundled with Windows 10
     * 1803+ and handles 7z on recent Windows 11 builds. If tar can't read it, the caller surfaces a
     * "extract manually with 7-Zip" message — acceptable while this is single-developer only.
     */
    static void extract7z(Path archive, Path destDir) throws IOException, InterruptedException {
        Files.createDirectories(destDir);
        int code = runProcess(destDir, "tar", "-xf", archive.toString(), "-C", destDir.toString());
        if (code != 0) {
            throw new IOException("`tar` exited " + code + " extracting " + archive.getFileName()
                    + ". Windows 11's bundled tar is required for .7z; otherwise extract it manually "
                    + "with 7-Zip into " + destDir + ".");
        }
    }

    /** Runs a command, streaming its merged stdout/stderr into the log, and returns the exit code. */
    static int runProcess(Path workdir, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        if (workdir != null) pb.directory(workdir.toFile());
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                Conjure.LOGGER.info("[bootstrap] | {}", line);
            }
        }
        return p.waitFor();
    }

    /** Starts a long-lived process (e.g. {@code ollama serve}) without waiting, logging to a file. */
    static void startDetached(Path workdir, Path logFile, String... cmd) throws IOException {
        Files.createDirectories(logFile.getParent());
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile());
        if (workdir != null) pb.directory(workdir.toFile());
        pb.start();
    }

    /** Resolves an executable on the Windows PATH via {@code where}, or null if not found. */
    static Path whereWindows(String exe) {
        try {
            Process p = new ProcessBuilder("where", exe).redirectErrorStream(true).start();
            String first;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                first = br.readLine();
            }
            if (p.waitFor() == 0 && first != null && !first.isBlank()) {
                return Path.of(first.trim());
            }
        } catch (IOException | InterruptedException ignored) {
            if (Thread.interrupted()) Thread.currentThread().interrupt();
        }
        return null;
    }

    private static String human(long bytes) {
        if (bytes < 0) return "unknown size";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.0f MB", mb);
        return String.format("%.1f GB", mb / 1024.0);
    }
}
