package dev.conjure.ai;

import dev.conjure.Config;

/** Builds the active providers from config. Call per-request so config edits take effect live. */
public final class ProviderFactory {

    public static TextModelProvider text() {
        if (Config.TEXT_MODE.get() == ProviderMode.ANTHROPIC) {
            return new AnthropicProvider(Config.ANTHROPIC_MODEL.get(), Config.ANTHROPIC_KEY_ENV.get());
        }
        return new OllamaProvider(Config.LOCAL_TEXT_ENDPOINT.get(), Config.LOCAL_TEXT_MODEL.get());
    }

    /**
     * Returns the active image provider, or {@code null} if image generation is not in LOCAL mode.
     *
     * <p>When {@link Config#IMAGE_MODE} is {@link ProviderMode#LOCAL} this builds a
     * {@link ComfyUIProvider} wired from the image config block:
     * <ul>
     *   <li>{@link ImageQuality#FAST} — {@code IMAGE_FAST_MODEL}, ~8 steps, 512px native</li>
     *   <li>{@link ImageQuality#HIGH} — {@code IMAGE_HIGH_MODEL}, ~25 steps, 768px native</li>
     * </ul>
     * The native size is the diffusion resolution; {@code TextureAgent} downscales it to the small
     * texture target. When mode is ANTHROPIC (Anthropic models do not emit images) {@code null} is
     * returned so the caller can fall back to the LLM pixel-art strategy.
     */
    public static ImageModelProvider image() {
        if (Config.IMAGE_MODE.get() != ProviderMode.LOCAL) {
            return null;
        }
        String endpoint = Config.LOCAL_IMAGE_ENDPOINT.get();
        ImageQuality quality = Config.IMAGE_QUALITY.get();
        if (quality == ImageQuality.HIGH) {
            return new ComfyUIProvider(endpoint, Config.IMAGE_HIGH_MODEL.get(), 25, 768);
        }
        // FAST (default)
        return new ComfyUIProvider(endpoint, Config.IMAGE_FAST_MODEL.get(), 8, 512);
    }

    private ProviderFactory() {}
}
