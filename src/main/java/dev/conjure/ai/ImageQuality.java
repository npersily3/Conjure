package dev.conjure.ai;

/**
 * Texture-generation quality tiers for the local image backend (A1111/Forge REST).
 *
 * <ul>
 *   <li>{@link #FAST} — a turbo/LCM model at a small size; ~seconds per texture, low VRAM. Default.</li>
 *   <li>{@link #HIGH} — a heavier model (e.g. FLUX) at a larger size; slow but showpiece quality.</li>
 * </ul>
 *
 * The concrete model name and pixel size for each tier come from {@link dev.conjure.Config}.
 */
public enum ImageQuality {
    FAST,
    HIGH
}
