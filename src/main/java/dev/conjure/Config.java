package dev.conjure;

import dev.conjure.ai.ProviderMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config (synced to a single TOML in the {@code config/} folder). Holds model routing
 * so the user can flip between local Ollama and Anthropic without touching code. API keys are
 * intentionally NOT stored here — only the name of the env var to read them from.
 */
public final class Config {

    public static final ModConfigSpec SPEC;

    // --- text / logic models ---
    public static final ModConfigSpec.EnumValue<ProviderMode> TEXT_MODE;
    public static final ModConfigSpec.ConfigValue<String> LOCAL_TEXT_ENDPOINT;
    public static final ModConfigSpec.ConfigValue<String> LOCAL_TEXT_MODEL;
    public static final ModConfigSpec.ConfigValue<String> ANTHROPIC_MODEL;
    public static final ModConfigSpec.ConfigValue<String> ANTHROPIC_KEY_ENV;

    // --- texture / image models ---
    public static final ModConfigSpec.EnumValue<ProviderMode> IMAGE_MODE;
    public static final ModConfigSpec.ConfigValue<String> LOCAL_IMAGE_ENDPOINT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Text / logic model routing (orchestrator, logic, data agents)").push("text");
        TEXT_MODE = b.comment("ANTHROPIC (cloud) or LOCAL (Ollama/LM Studio/llama.cpp)")
                 .defineEnum("provider", ProviderMode.LOCAL);
       LOCAL_TEXT_ENDPOINT = b.define("localEndpoint", "http://localhost:11434");
        LOCAL_TEXT_MODEL = b.define("localModel", "llama3.3:latest");
        ANTHROPIC_MODEL = b.define("anthropicModel", "claude-sonnet-4-6");
        ANTHROPIC_KEY_ENV = b.comment("Name of the environment variable holding the API key")
                .define("anthropicKeyEnv", "ANTHROPIC_API_KEY");
        b.pop();

        b.comment("Texture / model image generation (local open-source backend by default)").push("image");
        IMAGE_MODE = b.defineEnum("provider", ProviderMode.LOCAL);
        LOCAL_IMAGE_ENDPOINT = b.comment("e.g. ComfyUI http://localhost:8188 or A1111 http://localhost:7860")
                .define("localEndpoint", "http://localhost:8188");
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
