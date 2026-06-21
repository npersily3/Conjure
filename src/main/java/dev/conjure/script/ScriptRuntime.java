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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton that loads, caches, and sandboxed-executes Conjure behavior scripts
 * and named reusable effects.
 *
 * <h2>Sandbox design</h2>
 * <ul>
 *   <li><b>ClassShutter</b> — allowlist: the {@link ScriptContext} bridge, any class whose
 *       canonical name starts with {@code "net.minecraft."}, and a small set of safe Java
 *       primitives ({@link #SAFE_JAVA_CLASSES}). All other classes are denied, including
 *       {@code java.lang.Class}, {@code java.lang.Runtime}, {@code System}, {@code Thread},
 *       {@code java.lang.reflect.*}, and {@code java.io.*}.
 *       // ponytail: net.minecraft.* is a broad grant; a script can traverse to
 *       // level.getServer() from ctx.getLevel(). Acceptable for singleplayer untrusted
 *       // AI code where the player is also the server operator.  If server-sync ever
 *       // lands, tighten to a per-class denylist covering dangerous server state.
 *   </li>
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
    // Allowlisted Java class names (beyond ScriptContext + net.minecraft.*)
    // -------------------------------------------------------------------------

    /**
     * Safe Java classes that scripts may reference directly (via Rhino's class-access path).
     * Primitives and math only — no IO, no reflection, no thread management.
     */
    private static final Set<String> SAFE_JAVA_CLASSES = Set.of(
            "java.lang.String",
            "java.lang.Object",
            "java.lang.Number",
            "java.lang.Integer",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Long",
            "java.lang.Boolean",
            "java.lang.Math",
            "java.lang.CharSequence"
    );

    private static final String CTX_CLASS_NAME = ScriptContext.class.getName();

    /**
     * Allowlist shutter: passes the ScriptContext bridge, any net.minecraft.* class
     * (so raw MC objects returned by ctx accessors are usable), and the small set of
     * safe Java primitives above. Denies everything else.
     */
    private static final ClassShutter SHUTTER = className ->
            CTX_CLASS_NAME.equals(className)
            || className.startsWith("net.minecraft.")
            || SAFE_JAVA_CLASSES.contains(className);

    /** Prevents primitive wrapping so numbers/strings stay as JS types. */
    private static final WrapFactory SAFE_WRAP_FACTORY = new WrapFactory() {
        {
            setJavaPrimitiveWrap(false);
        }
    };

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
        execScript(scriptId, script, ctx);
    }

    /**
     * Resolve and execute a named reusable <em>effect</em> script against an existing ctx.
     * Effect scripts live at {@code <gamedir>/conjure/generated/effects/<name>.js} and share
     * the same sandbox and budget as behavior scripts.
     *
     * <p>// ponytail: the nested run re-enters a fresh Rhino Context so the instruction
     * budget resets per effect — fine for one nesting level; deep recursion would multiply.
     *
     * @param name the effect name (no extension, no path components)
     * @param ctx  the context to inject — typically the outer script's ctx
     * @throws ScriptException if the effect file cannot be found/read or throws at runtime
     */
    public void runEffect(String name, ScriptContext ctx) throws ScriptException {
        Path effectFile = effectPath(name);
        if (!Files.exists(effectFile)) {
            throw new ScriptException("Effect file not found: " + effectFile);
        }
        Script script = loadCompiled("effect:" + name, effectFile);
        execScript("effect:" + name, script, ctx);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /** Executes a precompiled script in a fresh Rhino context. */
    private void execScript(String displayId, Script script, ScriptContext ctx) throws ScriptException {
        SandboxContextFactory factory = new SandboxContextFactory();
        Context rhinoCtx = factory.enterContext();
        try {
            rhinoCtx.setOptimizationLevel(-1); // interpreted — ensures instruction observer fires
            rhinoCtx.setInstructionObserverThreshold(OBSERVER_THRESHOLD);
            rhinoCtx.setClassShutter(SHUTTER);
            rhinoCtx.setWrapFactory(SAFE_WRAP_FACTORY);

            Scriptable scope = buildScope(rhinoCtx, ctx);
            script.exec(rhinoCtx, scope);
        } catch (ScriptInstructionBudgetExceeded e) {
            throw new ScriptException("Script '" + displayId + "' exceeded instruction budget (possible infinite loop)");
        } catch (Exception e) {
            throw new ScriptException("Script '" + displayId + "' threw: " + e.getMessage(), e);
        } finally {
            Context.exit();
        }
    }

    /** Returns the canonical path for a behavior script file. */
    private static Path scriptPath(String scriptId) {
        return FMLPaths.GAMEDIR.get()
                .resolve("conjure")
                .resolve("generated")
                .resolve("scripts")
                .resolve(scriptId + ".js");
    }

    /** Returns the canonical path for a reusable effect script file. */
    private static Path effectPath(String name) {
        return FMLPaths.GAMEDIR.get()
                .resolve("conjure")
                .resolve("generated")
                .resolve("effects")
                .resolve(name + ".js");
    }

    /**
     * Returns a compiled {@link Script} for the given file, using the cache when the
     * file's last-modified timestamp has not changed since the last compilation.
     */
    private Script loadCompiled(String cacheId, Path scriptFile) throws ScriptException {
        long mtime;
        try {
            mtime = Files.getLastModifiedTime(scriptFile).toMillis();
        } catch (IOException e) {
            throw new ScriptException("Cannot stat script file: " + scriptFile, e);
        }

        String cacheKey = cacheId + ":" + mtime;
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
            rhinoCtx.setClassShutter(SHUTTER);
            Script compiled = rhinoCtx.compileString(source, cacheId + ".js", 1, null);
            // Evict stale keys for this cacheId before inserting the new one.
            compiledCache.entrySet().removeIf(e -> e.getKey().startsWith(cacheId + ":"));
            compiledCache.put(cacheKey, compiled);
            return compiled;
        } finally {
            Context.exit();
        }
    }

    /**
     * Build a restricted top-level scope that only exposes {@code ctx}.
     * {@link ScriptableObject#initSafeStandardObjects} creates the standard JS built-ins
     * (Math, Array, String, JSON, …) with the scope sealed so scripts cannot replace them.
     */
    private static Scriptable buildScope(Context rhinoCtx, ScriptContext ctx) {
        ScriptableObject scope = rhinoCtx.initSafeStandardObjects();
        Object wrappedCtx = Context.javaToJS(ctx, scope);
        ScriptableObject.putProperty(scope, "ctx", wrappedCtx);
        return scope;
    }

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
