package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class OllamaClient {

    private final WebClient webClient;
    private final String model;

    public OllamaClient(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.api-key:}") String apiKey,
            @Value("${ollama.model:qwen3:32b}") String model) {

        this.model = model;

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        this.webClient = builder.build();
    }

    /**
     * Простая генерация текста через /api/generate.
     * Подходит для "обычных" запросов, где не нужен строгий JSON.
     */
    public Mono<String> generateText(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                // отключаем отдельный "thinking"-вывод
                "think", false,
                "options", Map.of(
                        "temperature", 0.5,
                        "num_predict", 1000
                )
        );

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("response"));
    }

    /**
     * Генерация строго в JSON-формате (используется для парсинга сцен).
     * Добавляет параметр "format": "json" и "think": false.
     */
    public Mono<String> generateJson(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "format", "json",      // JSON mode в Ollama
                "think", false,        // не возвращать отдельный thinking-output
                "options", Map.of(
                        "temperature", 0.1,   // пониже, чтобы меньше фантазий
                        "num_predict", 1500
                )
        );

        return webClient.post()
                .uri("/api/generate")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("response"));
    }

    /**
     * Диалоговый режим через /api/chat.
     * Пока не используем в сцен-парсере, но можно держать для других задач.
     */
    public Mono<String> chat(List<Map<String, String>> messages) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "stream", false
        );

        return webClient.post()
                .uri("/api/chat")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map<String, Object> msg = (Map<String, Object>) response.get("message");
                    return msg != null ? (String) msg.get("content") : "";
                });
    }

    /**
     * Получение списка моделей.
     */
    public Mono<Map> listModels() {
        return webClient.get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(Map.class);
    }
}
