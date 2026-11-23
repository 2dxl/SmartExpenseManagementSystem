package llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 선문대학교 LLM(OpenAI 호환 {@code /v1/chat/completions}) 서버와 HTTP 통신하는 LlmClient 구현체.
 */
public class LocalLlmClient implements LlmClient {
    public static final String SUNMOON_LLM_ENDPOINT = "https://llm.deepaibit.com/v1/chat/completions";
    public static final String SUNMOON_DEFAULT_MODEL = "gemma-4-26b";
    public static final String SUNMOON_API_KEY_ENV = "SUNMOON_LLM_API_KEY";
    public static final String SUNMOON_MODEL_ENV = "SUNMOON_LLM_MODEL";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final URI endpoint;
    private final String model;
    private final Duration timeout;
    private final String apiKey;
    private final HttpClient httpClient;

    public LocalLlmClient(String endpointUrl, String model, Duration timeout, String apiKey) {
        if (endpointUrl == null || endpointUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("LLM 엔드포인트 URL은 비어 있을 수 없습니다.");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("모델명은 비어 있을 수 없습니다.");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("타임아웃은 0보다 커야 합니다.");
        }
        this.endpoint = URI.create(endpointUrl.trim());
        this.model = model.trim();
        this.timeout = timeout;
        this.apiKey = (apiKey == null || apiKey.trim().isEmpty()) ? null : apiKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /** 선문대학교 LLM API용 클라이언트를 기본 모델(gemma-4-26b)로 생성합니다. */
    public static LocalLlmClient forSunmoonLlm(String apiKey) {
        return forSunmoonLlm(apiKey, SUNMOON_DEFAULT_MODEL);
    }

    /** 선문대학교 LLM API용 클라이언트를 지정한 모델(gemma-4-26b, qwen-27b)로 생성합니다. */
    public static LocalLlmClient forSunmoonLlm(String apiKey, String model) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "선문대학교 LLM API 키가 비어 있습니다. .env 파일 또는 환경변수 "
                            + SUNMOON_API_KEY_ENV + "를 설정하세요.");
        }
        return new LocalLlmClient(SUNMOON_LLM_ENDPOINT, model, DEFAULT_TIMEOUT, apiKey);
    }

    /**
     * 실행 폴더의 {@code .env} 파일 또는 OS 환경변수에서 API 키와 모델명을 읽어
     * 선문대학교 LLM 클라이언트를 생성합니다.
     */
    public static LocalLlmClient forSunmoonLlmFromConfig() {
        return forSunmoonLlmFromEnvFile(Paths.get(".env"));
    }

    /** 지정한 {@code .env} 파일(없으면 OS 환경변수)에서 설정을 읽어 선문대학교 LLM 클라이언트를 생성합니다. */
    public static LocalLlmClient forSunmoonLlmFromEnvFile(Path envFile) {
        Map<String, String> fileValues = loadDotEnv(envFile);
        String apiKey = firstNonBlank(fileValues.get(SUNMOON_API_KEY_ENV), System.getenv(SUNMOON_API_KEY_ENV));
        String model = firstNonBlank(
                fileValues.get(SUNMOON_MODEL_ENV),
                System.getenv(SUNMOON_MODEL_ENV),
                SUNMOON_DEFAULT_MODEL);
        return forSunmoonLlm(apiKey, model);
    }

    /** 환경변수 또는 {@code .env} 파일에서 API 키를 읽어 선문대학교 LLM 클라이언트를 생성합니다. */
    public static LocalLlmClient forSunmoonLlmFromEnv() {
        return forSunmoonLlmFromConfig();
    }

    @Override
    public String ask(String systemPrompt, String userContent) {
        if (userContent == null || userContent.trim().isEmpty()) {
            throw new IllegalArgumentException("LLM 요청 내용(userContent)은 비어 있을 수 없습니다.");
        }

        String requestBody = buildChatCompletionBody(systemPrompt, userContent);
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

        if (apiKey != null) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException(
                    "LLM 서버 응답 시간이 초과되었습니다 (" + timeout.getSeconds() + "초, URL: " + endpoint + ").", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM 요청이 중단되었습니다.", e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "LLM 서버에 연결할 수 없습니다. 네트워크 및 서버 상태를 확인하세요 (URL: "
                            + endpoint + ", 원인: " + e.getMessage() + ").", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "LLM 서버 오류 응답 (HTTP " + response.statusCode() + "): " + response.body());
        }

        return extractAnswerText(response.body());
    }

    private String buildChatCompletionBody(String systemPrompt, String userContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escapeJson(model)).append("\",\"messages\":[");
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            sb.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"},");
        }
        sb.append("{\"role\":\"user\",\"content\":\"").append(escapeJson(userContent)).append("\"}");
        sb.append("]}");
        return sb.toString();
    }

    static String extractAnswerText(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalStateException("LLM 서버가 빈 응답을 반환했습니다.");
        }
        String extracted = extractJsonStringValue(json, "content");
        if (extracted == null) {
            throw new IllegalStateException("LLM 응답 JSON에서 'content' 필드를 찾을 수 없습니다: " + json);
        }
        return extracted;
    }

    private static Map<String, String> loadDotEnv(Path envFile) {
        Map<String, String> map = new HashMap<>();
        if (envFile == null || !Files.isRegularFile(envFile)) {
            return map;
        }
        try {
            for (String rawLine : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1).trim();
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = stripQuotes(line.substring(eq + 1).trim());
                if (!key.isEmpty() && !value.isEmpty()) {
                    map.put(key, value);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(".env 설정 파일을 읽는 중 오류가 발생했습니다: " + envFile, e);
        }
        return map;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
    }

    /** JSON 문자열에서 지정한 key의 첫 번째 문자열 값을 추출해 unescape합니다. */
    private static String extractJsonStringValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = 0;
        while ((idx = json.indexOf(pattern, idx)) >= 0) {
            int pos = idx + pattern.length();
            pos = skipWhitespace(json, pos);
            if (pos < json.length() && json.charAt(pos) == ':') {
                pos = skipWhitespace(json, pos + 1);
                if (pos < json.length() && json.charAt(pos) == '"') {
                    return readJsonString(json, pos + 1);
                }
            }
            idx += pattern.length();
        }
        return null;
    }

    private static int skipWhitespace(String s, int pos) {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private static String readJsonString(String s, int startAfterOpeningQuote) {
        StringBuilder out = new StringBuilder();
        for (int i = startAfterOpeningQuote; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '/':
                        out.append('/');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (i + 4 < s.length()) {
                            String hex = s.substring(i + 1, i + 5);
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                        break;
                    default:
                        out.append(next);
                        break;
                }
            } else {
                out.append(c);
            }
        }
        return null;
    }

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
