package dev.conjure.gen;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny shared flag for "is Conjure generating right now?". {@link GenerationService} bumps the
 * counter around every background task; the client HUD ({@code dev.conjure.client.ConjureHud})
 * reads it to draw a thinking indicator and to keep the game running while unfocused.
 *
 * <p>Common-side (no client imports) so it is safe to touch from the generation thread.
 */
public final class GenerationStatus {

    private static final AtomicInteger ACTIVE = new AtomicInteger();

    /** Mark one generation task as started. Always pair with {@link #end()} in a finally block. */
    public static void begin() { ACTIVE.incrementAndGet(); }

    /** Mark one generation task as finished. */
    public static void end() { ACTIVE.decrementAndGet(); }

    /** Whether at least one generation task is in flight. */
    public static boolean isActive() { return ACTIVE.get() > 0; }

    /** Number of in-flight generation tasks (never negative). */
    public static int count() { return Math.max(0, ACTIVE.get()); }

    private GenerationStatus() {}
}
