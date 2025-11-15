package ru.wink.winkaipreviz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class SceneToFluxPromptService {

    private final OllamaClient ollamaClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String enhancerSystemPrompt;
    private final String promptBuilderSystemPrompt;

    public SceneToFluxPromptService(OllamaClient ollamaClient) throws IOException {
        this.ollamaClient = ollamaClient;
        this.enhancerSystemPrompt = loadResource("prompts/SYSTEM-PROMPT-for-Missing-Data-Enhancer.md");
        this.promptBuilderSystemPrompt = loadResource("prompts/SYSTEM-PROMPT-for-Prompt-Builder(Flux-hint).md");
    }

    private String loadResource(String path) throws IOException {
        try (var in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Полный пайплайн:
     * base scene JSON → enriched JSON → Flux prompt
     */
    public FluxPromptResult generateFromSceneJson(String sceneJson) throws Exception {
        // 1. Обогащаем сцену (Missing-Data Enhancer)
        String enrichedJson = enhanceScene(sceneJson);

        // 2. Строим текстовый промпт для Flux (Prompt Builder)
        String fluxPrompt = buildFluxPrompt(enrichedJson);

        // 3. Достаём scene_id из enriched JSON (если он там есть)
        JsonNode root = mapper.readTree(enrichedJson);
        String sceneId = root.path("scene_id").asText(null);

        // 4. Возвращаем DTO
        return new FluxPromptResult(sceneId, enrichedJson, fluxPrompt);
    }

    /**
     * Шаг 1: LLM-энрихер — из base JSON делает enriched JSON по твоей spec.
     */
    private String enhanceScene(String sceneJson) throws Exception {
        String fullPrompt = enhancerSystemPrompt
                + "\n\nHere is the scene JSON:\n"
                + sceneJson;

        // Лучше использовать строгий JSON-режим
        String raw = ollamaClient.generateJson(fullPrompt).block();

        if (raw == null) {
            throw new IllegalStateException("LLM returned null response for enriched scene");
        }

        raw = cleanModelOutput(raw);
        if (raw.isBlank()) {
            throw new IllegalStateException("LLM returned empty response for enriched scene after cleaning");
        }

        String candidate = raw.trim();
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');

        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalStateException("LLM did not return a JSON object for enriched scene");
        }

        if (start != 0 || end != candidate.length() - 1) {
            candidate = candidate.substring(start, end + 1).trim();
        }

        JsonNode root = mapper.readTree(candidate);
        // Нормализуем JSON (убираем лишние пробелы/форматирование)
        return mapper.writeValueAsString(root);
    }

    /**
     * Шаг 2: Prompt Builder — из enriched JSON делает длинный текст-промпт для Flux.
     */
    private String buildFluxPrompt(String enrichedJson) throws Exception {
        String fullPrompt = promptBuilderSystemPrompt
                + "\n\nHere is the enriched JSON:\n"
                + enrichedJson;

        String raw = ollamaClient.generateText(fullPrompt).block();
        if (raw == null) {
            throw new IllegalStateException("LLM returned null response for Flux prompt");
        }

        raw = cleanModelOutput(raw);
        return raw.trim();
    }

    /**
     * Публичный метод для пересборки Flux prompt из enriched JSON (используется при обновлении слотов).
     */
    public String buildFluxPromptFromEnrichedJson(String enrichedJson) throws Exception {
        return buildFluxPrompt(enrichedJson);
    }

    /**
     * Убираем служебные теги и markdown-кодблоки из ответа модели.
     */
    private String cleanModelOutput(String raw) {
        if (raw == null) return "";

        String cleaned = raw;

        // 1) <think>...</think>
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>\\s*", "");

        // 2) ```json и просто ```
        cleaned = cleaned.replaceAll("(?is)```json", "");
        cleaned = cleaned.replaceAll("(?s)```", "");

        return cleaned.trim();
    }

    public record FluxPromptResult(String sceneId, String enrichedJson, String prompt) {}
}