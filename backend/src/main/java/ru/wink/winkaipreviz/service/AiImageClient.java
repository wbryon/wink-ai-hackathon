package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.wink.winkaipreviz.ai.ImageGenPort;
import ru.wink.winkaipreviz.ai.ImageResult;
import ru.wink.winkaipreviz.entity.DetailLevel;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class AiImageClient implements ImageGenPort {

    private final WebClient webClient;
    private final ExecutorService virtualExecutor;

    public AiImageClient(@Value("${ai.generate.base-url:http://ai:8000}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "wink-previz-ai-client/1.0 (Java21)")
                .build();

        // 🟢 создаём пул из виртуальных потоков
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public ImageResult generate(String prompt, DetailLevel level) {
        try (var scope = virtualExecutor) {
            // выполняем синхронный вызов в лёгком виртуальном потоке
            return scope.submit(() -> doGenerate(prompt, level)).get();
        } catch (Exception e) {
            return new ImageResult(null, "error", null, Instant.now());
        }
    }

    private ImageResult doGenerate(String prompt, DetailLevel level) {
        Map<String, Object> body = Map.of(
                "prompt", prompt,
                "lod", Optional.ofNullable(level).map(Enum::name).orElse("MID").toLowerCase()
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/generate")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            res -> res.createException().flatMap(Mono::error))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block(); // блокируем, но только виртуальный поток

            if (response == null) {
                return new ImageResult(null, "unknown", null, Instant.now());
            }

            String imageUrl = Optional.ofNullable(response.get("image_url")).map(Object::toString).orElse(null);
            String model = Optional.ofNullable(response.get("model")).map(Object::toString).orElse("unknown");
            Integer seed = response.get("seed") instanceof Number n ? n.intValue() : null;

            return new ImageResult(imageUrl, model, seed, Instant.now());
        } catch (WebClientResponseException e) {
            return new ImageResult(null, "http-" + e.getStatusCode().value(), null, Instant.now());
        } catch (Exception e) {
            return new ImageResult(null, "error", null, Instant.now());
        }
    }
}