package dev.conjure.ai;

/**
 * Texture-generation quality tiers for the local image backend (ComfyUI).
 *
 * <ul>
 *   <li>{@link #FAST} — fewer steps at 512px native; ~seconds per texture, low VRAM. Default.</li>
 *   <li>{@link #HIGH} — more steps at 768px native; slower but showpiece quality.</li>
 * </ul>
 *
 * The concrete checkpoint name and downscale size for each tier come from {@link dev.conjure.Config}.
 */
public enum ImageQuality {
    FAST,
    HIGH
}
