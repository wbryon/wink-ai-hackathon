package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ScriptProcessorClient {

	private static final Logger log = LoggerFactory.getLogger(ScriptProcessorClient.class);

	private final WebClient.Builder webClientBuilder;

	@Value("${script-processor.base-url:http://script-processor:8000}")
	private String baseUrl;

	@Value("${script-processor.model:Qwen/Qwen3-32B}")
	private String defaultModel;

	public ScriptProcessorClient(WebClient.Builder webClientBuilder) {
		this.webClientBuilder = webClientBuilder;
	}

	/**
	 * Получает список сцен из script-processor.
	 * @return список объектов SceneData с текстом и метаданными сцен
	 */
	public List<SceneData> splitToScenes(Path filePath) {
		return splitToScenes(filePath, null);
	}

	public List<SceneData> splitToScenes(Path filePath, String model) {
		String modelToUse = (model == null || model.isBlank()) ? defaultModel : model;
		log.info("Calling script-processor: url={}/split-script, model={}, file={}", baseUrl, modelToUse, filePath);

		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("file", new FileSystemResource(filePath));

		Map<String, Object> resp;
		try {
			resp = webClientBuilder
					.baseUrl(baseUrl)
					.build()
					.post()
					.uri(uriBuilder -> uriBuilder
							.path("/split-script")
							.queryParam("model", modelToUse)
							.queryParam("parse_mode", "auto")
							.build())
					.contentType(MediaType.MULTIPART_FORM_DATA)
					.bodyValue(builder.build())
					.exchangeToMono((ClientResponse res) -> {
						if (res.statusCode().is2xxSuccessful()) {
							return res.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
						} else {
							return res.bodyToMono(String.class).defaultIfEmpty("")
									.flatMap(body -> Mono.error(new RuntimeException(
											"script-processor error " + res.statusCode().value() + ": " + body)));
						}
					})
					.timeout(Duration.ofSeconds(120))
					.block();
		} catch (RuntimeException ex) {
			log.error("script-processor request failed: {}", ex.getMessage());
			throw ex;
		}

		if (resp == null) return List.of();
		Object scenesObj = resp.get("scenes");
		if (!(scenesObj instanceof List<?> list)) return List.of();

		List<SceneData> out = new ArrayList<>();
		for (Object o : list) {
			if (o instanceof Map<?, ?> m) {
				Object text = m.get("text");
				Object slugline = m.get("slugline");
				Object place = m.get("place");
				Object time = m.get("time");
				Object sceneIndex = m.get("scene_index");
				
				if (text != null) {
					out.add(new SceneData(
							sceneIndex != null ? ((Number) sceneIndex).intValue() : 0,
							slugline != null ? String.valueOf(slugline) : "",
							place != null ? String.valueOf(place) : null,
							time != null ? String.valueOf(time) : null,
							String.valueOf(text)
					));
				}
			}
		}
		log.info("script-processor returned {} scenes", out.size());
		return out;
	}

	/**
	 * Данные сцены из script-processor.
	 */
	public record SceneData(int sceneIndex, String slugline, String place, String time, String text) {}
}


