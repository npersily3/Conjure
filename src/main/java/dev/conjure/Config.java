package dev.conjure;

import dev.conjure.ai.ImageQuality;
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
    public static final ModConfigSpec.EnumValue<ImageQuality> IMAGE_QUALITY;
    public static final ModConfigSpec.ConfigValue<String> IMAGE_FAST_MODEL;
    public static final ModConfigSpec.ConfigValue<String> IMAGE_HIGH_MODEL;
    public static final ModConfigSpec.IntValue IMAGE_FAST_SIZE;
    public static final ModConfigSpec.IntValue IMAGE_HIGH_SIZE;

    // --- feature toggles (modularity) ---
    public static final ModConfigSpec.BooleanValue ENTITY_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue INTERACTIVITY_ENABLED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Text / logic model routing (orchestrator, logic, data agents)").push("text");
        TEXT_MODE = b.comment("ANTHROPIC (cloud) or LOCAL (Ollama/LM Studio/llama.cpp)")
                 .defineEnum("provider", ProviderMode.LOCAL);
       LOCAL_TEXT_ENDPOINT = b.define("localEndpoint", "http://127.0.0.1:11434");
        LOCAL_TEXT_MODEL = b.define("localModel", "llama3.3:latest");
        ANTHROPIC_MODEL = b.define("anthropicModel", "claude-sonnet-4-6");
        ANTHROPIC_KEY_ENV = b.comment("Name of the environment variable holding the API key")
                .define("anthropicKeyEnv", "ANTHROPIC_API_KEY");
        b.pop();

        b.comment("Texture / model image generation (local open-source backend by default)").push("image");
        IMAGE_MODE = b.comment("LOCAL (A1111/Forge REST) or ANTHROPIC (falls back to LLM pixel-art)")
                .defineEnum("provider", ProviderMode.LOCAL);
        LOCAL_IMAGE_ENDPOINT = b.comment("A1111/Forge web UI launched with --api, e.g. http://127.0.0.1:7860")
                .define("localEndpoint", "http://127.0.0.1:7860");
        IMAGE_QUALITY = b.comment("FAST (turbo model, small, ~seconds) or HIGH (e.g. FLUX, larger, slow)")
                .defineEnum("quality", ImageQuality.FAST);
        IMAGE_FAST_MODEL = b.comment("Checkpoint name for FAST mode; blank = whatever A1111 has loaded")
                .define("fastModel", "");
        IMAGE_HIGH_MODEL = b.comment("Checkpoint name for HIGH mode; blank = whatever A1111 has loaded")
                .define("highModel", "");
        IMAGE_FAST_SIZE = b.comment("Generated texture edge length (px) in FAST mode")
                .defineInRange("fastSize", 64, 16, 512);
        IMAGE_HIGH_SIZE = b.comment("Generated texture edge length (px) in HIGH mode")
                .defineInRange("highSize", 128, 16, 1024);
        b.pop();

        b.comment("Optional subsystems — turn off to disable without removing content").push("features");
        ENTITY_ANIMATIONS = b.comment("Play GeckoLib idle/walk animations on generated mobs (off = static pose)")
                .define("entityAnimations", true);
        INTERACTIVITY_ENABLED = b.comment("Allow generated blocks to be interactive (machines / scripted)")
                .define("interactivity", true);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
