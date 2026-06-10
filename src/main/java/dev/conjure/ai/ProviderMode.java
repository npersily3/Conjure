package dev.conjure.ai;

/** Where a given model call is routed. */
public enum ProviderMode {
    /** Anthropic Messages API (cloud). Requires an API key in the configured env var. */
    ANTHROPIC,
    /** A local, OpenAI/Ollama-compatible endpoint (Ollama, LM Studio, llama.cpp, ComfyUI). */
    LOCAL
}
