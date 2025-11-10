package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.wink.winkaipreviz.entity.DetailLevel;
import ru.wink.winkaipreviz.entity.Scene;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PromptCompilerClient {

	private final WebClient.Builder webClientBuilder;

	@Value("${prompt.compiler.base-url:http://prompt-compiler:8010}")
	private String compilerBaseUrl;

	public PromptCompilerClient(WebClient.Builder webClientBuilder) {
		this.webClientBuilder = webClientBuilder;
	}

	public String compile(Scene scene, DetailLevel level) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("scene_id", scene.getId() == null ? "" : scene.getId().toString());
		payload.put("title", scene.getTitle());
		payload.put("location", scene.getLocation());
		payload.put("description", scene.getSemanticSummary() != null ? scene.getSemanticSummary() : scene.getDescription());
		payload.put("characters", scene.getCharacters() == null ? new ArrayList<>() : new ArrayList<>(scene.getCharacters()));
		payload.put("props", scene.getProps() == null ? new ArrayList<>() : new ArrayList<>(scene.getProps()));
		payload.put("tone", toList(scene.getTone()));
		payload.put("style", toList(scene.getStyle()));
		payload.put("lod", level == null ? "sketch" : level.name().toLowerCase());

		Map<String, Object> resp = webClientBuilder
				.baseUrl(compilerBaseUrl)
				.build()
				.post()
				.uri("/compile")
				.bodyValue(payload)
				.retrieve()
				.onStatus(HttpStatusCode::isError, res -> res.createException().flatMap(Mono::error))
				.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
				.timeout(Duration.ofSeconds(30))
				.retryWhen(Retry.backoff(2, Duration.ofMillis(300)).maxBackoff(Duration.ofSeconds(2)))
				.block();

		if (resp == null) return "";
		Object p = resp.get("prompt");
		return p == null ? "" : String.valueOf(p);
	}

	private static List<String> toList(String value) {
		List<String> out = new ArrayList<>();
		if (value != null && !value.isBlank()) out.add(value);
		return out;
	}
}


