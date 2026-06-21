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
    public static final ModConfigSpec.ConfigValue<String> IMAGE_HIGH_LORA;
    public static final ModConfigSpec.IntValue IMAGE_FAST_SIZE;
    public static final ModConfigSpec.IntValue IMAGE_HIGH_SIZE;

    // --- feature toggles (modularity) ---
    public static final ModConfigSpec.BooleanValue ENTITY_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue INTERACTIVITY_ENABLED;
    public static final ModConfigSpec.BooleanValue RECIPES_ENABLED;
    public static final ModConfigSpec.BooleanValue AUTO_INSTALL_BACKENDS;
    public static final ModConfigSpec.BooleanValue SHOW_INTENT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Text / logic model routing (orchestrator, logic, data agents)").push("text");
        TEXT_MODE = b.comment("ANTHROPIC (cloud) or LOCAL (Ollama/LM Studio/llama.cpp)")
                 .defineEnum("provider", ProviderMode.LOCAL);
       LOCAL_TEXT_ENDPOINT = b.define("localEndpoint", "http://127.0.0.1:11434");
        LOCAL_TEXT_MODEL = b.define("localModel", "gemma4:latest");
        ANTHROPIC_MODEL = b.define("anthropicModel", "claude-sonnet-4-6");
        ANTHROPIC_KEY_ENV = b.comment("Name of the environment variable holding the API key")
                .define("anthropicKeyEnv", "ANTHROPIC_API_KEY");
        b.pop();

        b.comment("Texture / model image generation (local open-source backend by default)").push("image");
        IMAGE_MODE = b.comment("LOCAL (ComfyUI) or ANTHROPIC (falls back to LLM pixel-art)")
                .defineEnum("provider", ProviderMode.LOCAL);
        LOCAL_IMAGE_ENDPOINT = b.comment("ComfyUI server, e.g. http://127.0.0.1:8188")
                .define("localEndpoint", "http://127.0.0.1:8188");
        IMAGE_QUALITY = b.comment("FAST (SD1.5 pixel checkpoint, 512px, ~seconds) or HIGH (SDXL+LoRA, 1024px, slower)")
                .defineEnum("quality", ImageQuality.FAST);
        IMAGE_FAST_MODEL = b.comment("ComfyUI checkpoint for FAST mode — a light SD1.5 PIXEL-ART checkpoint.",
                        "Auto-downloaded on first launch. Must exist in ComfyUI/models/checkpoints.")
                .define("fastModel", "PixelartSpritesheet_V.1.ckpt");
        IMAGE_HIGH_MODEL = b.comment("ComfyUI checkpoint for HIGH mode — SDXL base (paired with IMAGE highLora).",
                        "Auto-downloaded on first launch. Needs ~8GB VRAM. See README 'Texture quality'.")
                .define("highModel", "sd_xl_base_1.0.safetensors");
        IMAGE_HIGH_LORA = b.comment("LoRA applied in HIGH mode (Pixel Art XL). Must exist in ComfyUI/models/loras.",
                        "Auto-downloaded on first launch. Leave blank to use the SDXL base alone.")
                .define("highLora", "pixel-art-xl.safetensors");
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
        RECIPES_ENABLED = b.comment(
                "Generate survival recipes for blocks/items, and expand building-material blocks into",
                "a family (smooth/bricks/slab/stairs/wall) wired by vanilla-pattern recipes (off = a",
                "single creative-only block per prompt).")
                .define("recipes", true);
        AUTO_INSTALL_BACKENDS = b.comment(
                "On first launch, automatically download/install the AI backends this mod needs",
                "(Ollama + the local text model, and ComfyUI + a checkpoint). Windows-only for now;",
                "downloads land under <gamedir>/conjure/runtime/. Set false to manage them yourself.")
                .define("autoInstallBackends", true);
        SHOW_INTENT = b.comment(
                "DEV/DEBUG: append the generated visual + usage intent (in red) to every conjured",
                "block/item tooltip. Lets you tell a texture/model failure (texture ≠ visual intent)",
                "from a behavior gap (the code can't do what the usage intent describes). Off for release.")
                .define("showIntent", true);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
