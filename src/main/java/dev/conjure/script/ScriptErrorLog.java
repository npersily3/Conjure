package dev.conjure.script;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded in-memory log of behavior-script runtime failures, collected during play so an agent can
 * fix them after the fact (via {@code /conjure fixscripts}) — the live half of the "review channel"
 * that catches what static pre-release review can't. Keyed by scriptId so repeated failures of the
 * same script collapse to its latest error. Thread-safe: scripts fail on the server thread; the fix
 * command drains from the command thread.
 */
public final class ScriptErrorLog {

    private static final int MAX = 64;
    /** scriptId → most recent error message (insertion-ordered; oldest evicted past MAX). */
    private static final Map<String, String> ERRORS = new LinkedHashMap<>();

    private ScriptErrorLog() {}

    /** Records (or refreshes) the latest runtime error for a script. */
    public static synchronized void record(String scriptId, String error) {
        if (scriptId == null || scriptId.isBlank()) return;
        ERRORS.remove(scriptId); // re-insert at end so it counts as most-recent
        ERRORS.put(scriptId, error == null ? "unknown error" : error);
        while (ERRORS.size() > MAX) ERRORS.remove(ERRORS.keySet().iterator().next());
    }

    /** Returns a snapshot of (scriptId → latest error) and clears the log. */
    public static synchronized Map<String, String> drain() {
        Map<String, String> copy = new LinkedHashMap<>(ERRORS);
        ERRORS.clear();
        return copy;
    }

    public static synchronized int size() { return ERRORS.size(); }
}
