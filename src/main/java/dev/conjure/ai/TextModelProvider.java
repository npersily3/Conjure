package dev.conjure.ai;

/**
 * A text/code-generating model. Implementations are swappable at runtime via config, so the
 * same orchestrator/logic/data agents can run against Anthropic or a local model unchanged.
 */
public interface TextModelProvider {

    /**
     * @param system system prompt (role/instructions for the agent); may be null/blank
     * @param user   the user/task content
     * @return the model's text completion
     */
    String complete(String system, String user) throws Exception;

    /** Human-readable id for logs, e.g. "ollama:llama3.1" or "anthropic:claude-sonnet-4-6". */
    String id();
}
