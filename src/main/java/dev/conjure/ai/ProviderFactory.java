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

    // ImageModelProvider image() — added when the texture agent lands (ComfyUI client or
    // the pixel-array-via-text strategy).

    private ProviderFactory() {}
}
