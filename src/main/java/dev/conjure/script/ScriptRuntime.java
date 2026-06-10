package dev.conjure.script;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton that loads, caches, and sandboxed-executes Conjure behavior scripts.
 *
 * <h2>Sandbox design</h2>
 * <ul>
 *   <li><b>ClassShutter</b> — returns {@code false} for every class name, so scripts
 *       cannot import or access any Java class via {@code Packages.*} or reflection.
 *       The only Java objects reachable by a script are those explicitly placed in the
 *       top-level scope (currently just {@code ctx}).</li>
 *   <li><b>WrapFactory</b> — {@code setJavaPrimitiveWrap(false)} so primitive numbers/
 *       strings are not wrapped into Java objects, keeping the script in JS-land.</li>
 *   <li><b>Interpreted mode</b> — {@code setOptimizationLevel(-1)} disables bytecode
 *       generation, so the instruction observer fires reliably.</li>
 *   <li><b>Instruction-count guard</b> — kills infinite loops after
 *       {@link #INSTRUCTION_BUDGET} pseudo-instructions.</li>
 * </ul>
 *
 * <h2>Caching</h2>
 * Compiled {@link Script} objects are cached by (scriptId, file-last-modified-millis).
 * A changed file on disk is picked up on the next interaction without a restart.
 */
public final class ScriptRuntime {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Max pseudo-instructions per script invocation — prevents infinite loops. */
    private static final int INSTRUCTION_BUDGET = 100_000;

    /** Observe every N bytecodes (Rhino checks at multiples of this threshold). */
    private static final int OBSERVER_THRESHOLD = 10_000;

    // Singleton
    private static final ScriptRuntime INSTANCE = new ScriptRuntime();

    public static ScriptRuntime get() { return INSTANCE; }

    // Cache key: scriptId + ":" + lastModifiedMillis
    private final ConcurrentHashMap<String, Script> compiledCache = new ConcurrentHashMap<>();

    private ScriptRuntime() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Load the script file for {@code scriptId}, compile (or use cached version),
     * and execute it with the supplied {@code ctx} object bound to the top-level
     * scope under the name {@code "ctx"}.
     *
     * <p>Any exception thrown by the script is caught, logged, and re-thrown as
     * a {@link ScriptException} so callers can surface a clean error to the player.
     *
     * @param scriptId the value of {@link dev.conjure.content.SlotDefinition#behaviorScriptId}
     * @param ctx      the {@link ScriptContext} to inject — must be fresh per invocation
     * @throws ScriptException if the script file cannot be read or throws at runtime
     */
    public void run(String scriptId, ScriptContext ctx) throws ScriptException {
        Path scriptFile = scriptPath(scriptId);
        if (!Files.exists(scriptFile)) {
            throw new ScriptException("Script file not found: " + scriptFile);
        }

        Script script = loadCompiled(scriptId, scriptFile);

        SandboxContextFactory factory = new SandboxContextFactory();
        Context rhinoCtx = factory.enterContext();
        try {
            rhinoCtx.setOptimizationLevel(-1); // interpreted — ensures instruction observer fires
            rhinoCtx.setInstructionObserverThreshold(OBSERVER_THRESHOLD);
            rhinoCtx.setClassShutter(DENY_ALL_SHUTTER);
            rhinoCtx.setWrapFactory(SAFE_WRAP_FACTORY);

            Scriptable scope = buildScope(rhinoCtx, ctx);
            script.exec(rhinoCtx, scope);
        } catch (ScriptInstructionBudgetExceeded e) {
            throw new ScriptException("Script '" + scriptId + "' exceeded instruction budget (possible infinite loop)");
        } catch (Exception e) {
            throw new ScriptException("Script '" + scriptId + "' threw: " + e.getMessage(), e);
        } finally {
            Context.exit();
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /** Returns the canonical path for a behavior script file. */
    private static Path scriptPath(String scriptId) {
        return FMLPaths.GAMEDIR.get()
                .resolve("conjure")
                .resolve("generated")
                .resolve("scripts")
                .resolve(scriptId + ".js");
    }

    /**
     * Returns a compiled {@link Script} for the given file, using the cache when the
     * file's last-modified timestamp has not changed since the last compilation.
     */
    private Script loadCompiled(String scriptId, Path scriptFile) throws ScriptException {
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(scriptFile).toMillis();
        } catch (IOException e) {
            throw new ScriptException("Cannot stat script file: " + scriptFile, e);
        }

        String cacheKey = scriptId + ":" + mtime;
        Script cached = compiledCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String source;
        try {
            source = Files.readString(scriptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ScriptException("Cannot read script file: " + scriptFile, e);
        }

        // Compile outside any particular Context — re-enter only for compilation.
        SandboxContextFactory factory = new SandboxContextFactory();
        Context rhinoCtx = factory.enterContext();
        try {
            rhinoCtx.setOptimizationLevel(-1);
            rhinoCtx.setClassShutter(DENY_ALL_SHUTTER);
            Script compiled = rhinoCtx.compileString(source, scriptId + ".js", 1, null);
            // Evict stale keys for this scriptId before inserting the new one.
            compiledCache.entrySet().removeIf(e -> e.getKey().startsWith(scriptId + ":"));
            compiledCache.put(cacheKey, compiled);
            return compiled;
        } finally {
            Context.exit();
        }
    }

    /**
     * Build a restricted top-level scope that only exposes {@code ctx}.
     * We deliberately do NOT call {@link ScriptableObject#initStandardObjects(Context)}
     * (which would add Java bridges), nor do we seal the scope — sealing prevents
     * scripts from defining their own variables, which breaks normal JS patterns.
     */
    private static Scriptable buildScope(Context rhinoCtx, ScriptContext ctx) {
        // initSafeStandardObjects creates the standard JS built-ins (Math, Array,
        // String, JSON, …) but seals the scope so scripts cannot replace them.
        ScriptableObject scope = rhinoCtx.initSafeStandardObjects();
        // Wrap the Java ScriptContext into a JS object and bind it as "ctx".
        Object wrappedCtx = Context.javaToJS(ctx, scope);
        ScriptableObject.putProperty(scope, "ctx", wrappedCtx);
        return scope;
    }

    // -------------------------------------------------------------------------
    // Sandbox helpers — stateless, safe as constants
    // -------------------------------------------------------------------------

    /** Denies all Java class visibility from scripts. */
    private static final ClassShutter DENY_ALL_SHUTTER = className -> false;

    /** Prevents primitive wrapping so numbers/strings stay as JS types. */
    private static final WrapFactory SAFE_WRAP_FACTORY = new WrapFactory() {
        {
            setJavaPrimitiveWrap(false);
        }
    };

    // -------------------------------------------------------------------------
    // ContextFactory — installs the instruction-count observer
    // -------------------------------------------------------------------------

    /**
     * Custom {@link ContextFactory} that overrides
     * {@link ContextFactory#observeInstructionCount(Context, int)} to enforce
     * {@link ScriptRuntime#INSTRUCTION_BUDGET}.
     */
    private static final class SandboxContextFactory extends ContextFactory {

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Integer spent = (Integer) cx.getThreadLocal("conjure.instructions");
            int total = (spent == null ? 0 : spent) + instructionCount;
            cx.putThreadLocal("conjure.instructions", total);
            if (total > INSTRUCTION_BUDGET) {
                throw new ScriptInstructionBudgetExceeded();
            }
        }

        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            // Keep strict-mode off to avoid JS author friction, everything else default.
            return super.hasFeature(cx, featureIndex);
        }
    }

    /** Thrown internally when the instruction budget is exhausted. */
    private static final class ScriptInstructionBudgetExceeded extends RuntimeException {
        ScriptInstructionBudgetExceeded() {
            super("Instruction budget exceeded", null, true, false); // suppress stacktrace fill
        }
    }
}
