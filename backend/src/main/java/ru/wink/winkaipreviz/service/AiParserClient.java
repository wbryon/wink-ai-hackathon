package ru.wink.winkaipreviz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.wink.winkaipreviz.ai.ParserPort;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.entity.SceneStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AiParserClient implements ParserPort {

	private final WebClient.Builder webClientBuilder;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${ai.parser.base-url:http://ai:8000}")
	private String parserBaseUrl;

	private final List<String> rawJsonHistory = new ArrayList<>();
	private String lastRawJson = null;

	public AiParserClient(WebClient.Builder webClientBuilder) {
		this.webClientBuilder = webClientBuilder;
	}

	public List<Scene> parseScenes(String fullText) {
		try {
			Map<String, Object> response = webClientBuilder
					.baseUrl(parserBaseUrl)
					.build()
					.post()
					.uri("/parse")
					.bodyValue(Map.of("text", fullText))
					.retrieve()
					.onStatus(HttpStatusCode::isError, res -> res.createException().flatMap(Mono::error))
					.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
					.timeout(Duration.ofSeconds(60))
					.retryWhen(Retry.backoff(2, Duration.ofMillis(300)).maxBackoff(Duration.ofSeconds(2)))
					.block();

			if (response == null) return List.of();

			saveRawJson(response);

			Object scenesObj = response.get("scenes");
			if (!(scenesObj instanceof List<?> list)) return List.of();

			return list.stream()
					.filter(o -> o instanceof Map<?, ?>)
					.map(o -> (Map<?, ?>) o)
					.map(this::mapToScene)
					.toList();

		} catch (Exception ex) {
			return List.of();
		}
	}

    private void saveRawJson(Map<String, Object> response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            this.lastRawJson = json;
            this.rawJsonHistory.add(json);
        } catch (JsonProcessingException e) {
            String raw = String.valueOf(response);
            this.lastRawJson = raw;
            this.rawJsonHistory.add(raw);
        }
    }

    private Scene mapToScene(Map<?, ?> m) {
        Scene s = new Scene();
        s.setTitle(asString(m.get("title")));
        s.setLocation(asString(m.get("location")));
        s.setDescription(asString(m.get("description")));
        s.setSemanticSummary(asString(m.get("summary")));
        s.setTone(asString(m.get("tone")));
        s.setStyle(asString(m.get("style")));
        s.setCharacters(asStringList(m.get("characters")));
        s.setProps(asStringList(m.get("props")));
        s.setStatus(SceneStatus.PARSED);
        return s;
    }

	public String getLastRawJson() {
		return lastRawJson;
	}

	public List<String> getRawJsonHistory() {
		return new ArrayList<>(rawJsonHistory);
	}

	public void clearRawJsonHistory() {
		rawJsonHistory.clear();
	}

	private static String asString(Object v) {
		return v == null ? null : String.valueOf(v);
	}

	private static List<String> asStringList(Object v) {
		if (v instanceof List<?> l) {
			List<String> out = new ArrayList<>();
			for (Object it : l) out.add(asString(it));
			return out;
		}
		return new ArrayList<>();
	}
}

