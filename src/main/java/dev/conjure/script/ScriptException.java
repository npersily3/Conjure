package dev.conjure.script;

/**
 * Thrown by {@link ScriptRuntime} when a behavior script cannot be loaded or
 * raises an unhandled exception at runtime.
 *
 * <p>Callers (item/block interaction handlers) should catch this, log it, and
 * surface a short message to the player rather than propagating it up the stack.
 */
public final class ScriptException extends Exception {

    public ScriptException(String message) {
        super(message);
    }

    public ScriptException(String message, Throwable cause) {
        super(message, cause);
    }
}
