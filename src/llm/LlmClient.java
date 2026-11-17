package llm;

/** 로컬 LLM API 호출을 위한 공통 인터페이스. */
public interface LlmClient {
    String ask(String systemPrompt, String userContent);
}
