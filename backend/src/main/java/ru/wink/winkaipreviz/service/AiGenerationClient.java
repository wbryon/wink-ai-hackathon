package ru.wink.winkaipreviz.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;

@Component
public class AiGenerationClient {

	private final WebClient webClient;
	private final int retries;
	private final Duration timeout;

	public AiGenerationClient(
			@Value("${ai.service.url:${AI_SERVICE_URL:http://ai:8000}}") String baseUrl,
			@Value("${app.ai.timeout-ms:5000}") int timeoutMs,
			@Value("${app.ai.retries:1}") int retries
	) {
		HttpClient httpClient = HttpClient.create()
			.responseTimeout(Duration.ofMillis(timeoutMs));
		this.webClient = WebClient.builder()
			.baseUrl(baseUrl)
			.clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
			.build();
		this.retries = retries;
		this.timeout = Duration.ofMillis(timeoutMs);
	}

	public GenerateResponse generate(UUID sceneId, String prompt, String lod) {
		GenerateRequest req = new GenerateRequest(sceneId.toString(), prompt, lod);
		try {
			return webClient.post()
				.uri("/generate")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(req)
				.retrieve()
				.bodyToMono(GenerateResponse.class)
				.retryWhen(Retry.backoff(retries, Duration.ofMillis(200))
					.filter(throwable -> throwable instanceof WebClientResponseException wcre && wcre.getStatusCode().is5xxServerError()))
				.timeout(timeout)
				.onErrorResume(ex -> Mono.empty())
				.blockOptional()
				.orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	public static class GenerateRequest {
		@JsonProperty("scene_id")
		public String sceneId;
		@JsonProperty("prompt")
		public String prompt;
		@JsonProperty("lod")
		public String lod;

		public GenerateRequest(String sceneId, String prompt, String lod) {
			this.sceneId = sceneId;
			this.prompt = prompt;
			this.lod = lod;
		}
	}

	public static class GenerateResponse {
		@JsonProperty("image_url")
		public String imageUrl;
		@JsonProperty("seed")
		public Integer seed;
	}
}


