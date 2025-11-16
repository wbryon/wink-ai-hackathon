package ru.wink.winkaipreviz.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class SceneToFluxPromptService {

    private static final Logger log = LoggerFactory.getLogger(SceneToFluxPromptService.class);

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
        log.info("=== Starting Flux prompt generation pipeline ===");
        log.debug("Input base JSON (first 500 chars): {}", 
                sceneJson.length() > 500 ? sceneJson.substring(0, 500) + "..." : sceneJson);
        
        // 1. Обогащаем сцену (Missing-Data Enhancer)
        String enrichedJson = enhanceScene(sceneJson);

        // 2. Строим текстовый промпт для Flux (Prompt Builder)
        String fluxPrompt = buildFluxPrompt(enrichedJson);

        // 3. Достаём scene_id из enriched JSON (если он там есть)
        JsonNode root = mapper.readTree(enrichedJson);
        String sceneId = root.path("scene_id").asText(null);

        log.info("=== Flux prompt generation pipeline completed ===");
        log.info("Generated prompt (first 300 chars): {}", 
                fluxPrompt.length() > 300 ? fluxPrompt.substring(0, 300) + "..." : fluxPrompt);

        // 4. Возвращаем DTO
        return new FluxPromptResult(sceneId, enrichedJson, fluxPrompt);
    }

    /**
     * Шаг 1: LLM-энрихер — из base JSON делает enriched JSON по твоей spec.
     */
    private String enhanceScene(String sceneJson) throws Exception {
        log.info("=== [ENRICHMENT] Step 1: Enhancing base JSON → enriched JSON ===");
        log.info("[ENRICHMENT] Input base JSON length: {} chars", sceneJson.length());
        log.debug("[ENRICHMENT] Input base JSON:\n{}", sceneJson);
        
        // Логируем ключевые поля base JSON для сравнения
        try {
            JsonNode baseNode = mapper.readTree(sceneJson);
            if (baseNode.has("slugline_raw")) {
                log.info("[ENRICHMENT] Base JSON slugline_raw: '{}'", baseNode.get("slugline_raw").asText());
            }
            if (baseNode.has("location")) {
                JsonNode locNode = baseNode.get("location");
                if (locNode.isObject() && locNode.has("raw")) {
                    log.info("[ENRICHMENT] Base JSON location.raw: '{}'", locNode.get("raw").asText());
                }
            }
            if (baseNode.has("characters")) {
                JsonNode charsNode = baseNode.get("characters");
                if (charsNode.isArray()) {
                    log.info("[ENRICHMENT] Base JSON characters count: {}", charsNode.size());
                }
            }
        } catch (Exception e) {
            log.warn("[ENRICHMENT] Failed to parse base JSON for logging: {}", e.getMessage());
        }

        String fullPrompt = enhancerSystemPrompt
                + "\n\nHere is the scene JSON:\n"
                + sceneJson;
        
        log.info("[ENRICHMENT] Calling LLM to enrich JSON (prompt length: {} chars)", fullPrompt.length());

        // Повторные попытки с увеличением num_predict при неполном JSON
        int[] numPredictValues = {2000, 3000, 4000}; // Увеличиваем лимит токенов при повторах
        Exception lastException = null;
        
        for (int attempt = 0; attempt < numPredictValues.length; attempt++) {
            try {
                int numPredict = numPredictValues[attempt];
                log.debug("Attempt {}: requesting enriched JSON with num_predict={}", attempt + 1, numPredict);
                
                // Используем generateJson с увеличенным num_predict
                String raw = ollamaClient.generateJson(fullPrompt, numPredict).block();

                if (raw == null) {
                    throw new IllegalStateException("LLM returned null response for enriched scene");
                }

                log.debug("LLM raw response length: {} chars (first 1000 chars): {}", 
                        raw.length(), raw.length() > 1000 ? raw.substring(0, 1000) + "..." : raw);

                raw = cleanModelOutput(raw);
                if (raw.isBlank()) {
                    throw new IllegalStateException("LLM returned empty response for enriched scene after cleaning");
                }

                String candidate = raw.trim();
                int start = candidate.indexOf('{');
                int end = candidate.lastIndexOf('}');

                if (start == -1 || end == -1 || end <= start) {
                    // Проверяем, не обрезан ли JSON (нет закрывающей скобки)
                    if (start != -1 && end == -1) {
                        log.warn("JSON appears incomplete (missing closing brace). Response length: {} chars. Attempt: {}", 
                                candidate.length(), attempt + 1);
                        if (attempt < numPredictValues.length - 1) {
                            lastException = new IllegalStateException("JSON incomplete - missing closing brace");
                            continue; // Повторяем с большим num_predict
                        }
                    }
                    throw new IllegalStateException("LLM did not return a JSON object for enriched scene");
                }

                if (start != 0 || end != candidate.length() - 1) {
                    candidate = candidate.substring(start, end + 1).trim();
                }

                // Проверяем баланс скобок перед парсингом
                long openBraces = candidate.chars().filter(ch -> ch == '{').count();
                long closeBraces = candidate.chars().filter(ch -> ch == '}').count();
                if (openBraces != closeBraces) {
                    log.warn("JSON braces unbalanced: {} open, {} close. Response length: {} chars. Attempt: {}", 
                            openBraces, closeBraces, candidate.length(), attempt + 1);
                    if (attempt < numPredictValues.length - 1) {
                        lastException = new IllegalStateException("JSON braces unbalanced");
                        continue; // Повторяем с большим num_predict
                    }
                }

                JsonNode root = mapper.readTree(candidate);
                // Нормализуем JSON (убираем лишние пробелы/форматирование)
                String enrichedJson = mapper.writeValueAsString(root);
                
                log.info("[ENRICHMENT] ✓ Enriched JSON generated successfully on attempt {}", attempt + 1);
                log.info("[ENRICHMENT] Enriched JSON length: {} chars (base was {} chars)", 
                        enrichedJson.length(), sceneJson.length());
                log.info("[ENRICHMENT] Enriched JSON (first 1500 chars): {}", 
                        enrichedJson.length() > 1500 ? enrichedJson.substring(0, 1500) + "..." : enrichedJson);
                log.debug("[ENRICHMENT] Full enriched JSON:\n{}", enrichedJson);
                
                // Логируем ключевые поля для быстрой диагностики
                try {
                    JsonNode enrichedNode = mapper.readTree(enrichedJson);
                    
                    // Логируем ключевые поля enriched JSON для сравнения с base
                    if (enrichedNode.has("slugline_raw")) {
                        log.info("[ENRICHMENT] ✓ Enriched slugline_raw: '{}'", enrichedNode.get("slugline_raw").asText());
                    }
                    
                    if (enrichedNode.has("location")) {
                        JsonNode locationNode = enrichedNode.get("location");
                        if (locationNode.isObject()) {
                            String locationRaw = locationNode.has("raw") ? locationNode.get("raw").asText() : "N/A";
                            String locationNorm = locationNode.has("norm") ? locationNode.get("norm").asText() : "N/A";
                            String locationDesc = locationNode.has("description") ? locationNode.get("description").asText() : "N/A";
                            log.info("[ENRICHMENT] ✓ Enriched location: raw='{}', norm='{}'", locationRaw, locationNorm);
                            log.info("[ENRICHMENT] ✓ Enriched location.description: '{}'", 
                                    locationDesc.length() > 150 ? locationDesc.substring(0, 150) + "..." : locationDesc);
                            if (locationNode.has("environment_details")) {
                                JsonNode envDetails = locationNode.get("environment_details");
                                if (envDetails.isArray()) {
                                    log.info("[ENRICHMENT] ✓ Enriched location.environment_details count: {}", envDetails.size());
                                }
                            }
                        }
                    }
                    if (enrichedNode.has("characters")) {
                        JsonNode charsNode = enrichedNode.get("characters");
                        if (charsNode.isArray()) {
                            log.info("[ENRICHMENT] ✓ Enriched characters count: {}", charsNode.size());
                            for (int i = 0; i < Math.min(charsNode.size(), 5); i++) {
                                JsonNode charNode = charsNode.get(i);
                                String charName = charNode.has("name") ? charNode.get("name").asText() : "N/A";
                                String charRole = charNode.has("role") ? charNode.get("role").asText() : "N/A";
                                log.info("[ENRICHMENT] ✓ Character {}: name='{}', role='{}'", i + 1, charName, charRole);
                            }
                        } else {
                            log.warn("[ENRICHMENT] Enriched JSON has 'characters' field but it's not an array: {}", charsNode.getNodeType());
                        }
                    } else {
                        log.warn("[ENRICHMENT] Enriched JSON does not have 'characters' field");
                    }
                    
                    // Логируем дополнительные поля, которые появляются только в enriched JSON
                    if (enrichedNode.has("camera")) {
                        log.info("[ENRICHMENT] ✓ Enriched JSON contains 'camera' field");
                    }
                    if (enrichedNode.has("lighting")) {
                        log.info("[ENRICHMENT] ✓ Enriched JSON contains 'lighting' field");
                    }
                    if (enrichedNode.has("mood")) {
                        JsonNode moodNode = enrichedNode.get("mood");
                        if (moodNode.isArray()) {
                            log.info("[ENRICHMENT] ✓ Enriched mood: {}", moodNode.toString());
                        }
                    }
                } catch (Exception e) {
                    log.warn("[ENRICHMENT] Failed to parse enriched JSON for diagnostic logging: {}", e.getMessage(), e);
                }
                
                log.info("[ENRICHMENT] === Step 1 completed: base JSON → enriched JSON ===");
                
                // Успешно распарсили JSON, выходим из цикла
                return enrichedJson;
                
            } catch (JsonParseException e) {
                // JSON обрезан или невалидный - пробуем с большим num_predict
                log.warn("JSON parsing failed due to incomplete or invalid response. Attempt: {}. Error: {}", 
                        attempt + 1, e.getMessage());
                lastException = e;
                if (attempt < numPredictValues.length - 1) {
                    continue; // Повторяем с большим num_predict
                }
            } catch (JsonMappingException e) {
                // Другие ошибки парсинга JSON
                log.warn("JSON parsing failed. Attempt: {}. Error: {}", attempt + 1, e.getMessage());
                lastException = e;
                if (attempt < numPredictValues.length - 1) {
                    continue; // Повторяем с большим num_predict
                }
            } catch (Exception e) {
                // Другие ошибки
                log.warn("Unexpected error during JSON generation. Attempt: {}. Error: {}", attempt + 1, e.getMessage());
                lastException = e;
                if (attempt < numPredictValues.length - 1) {
                    continue; // Повторяем с большим num_predict
                }
            }
        }
        
        // Все попытки исчерпаны
        if (lastException != null) {
            throw new IllegalStateException("Failed to generate valid enriched JSON after " + 
                    numPredictValues.length + " attempts with increasing num_predict. Last error: " + 
                    lastException.getMessage(), lastException);
        } else {
            throw new IllegalStateException("Failed to generate valid enriched JSON after " + 
                    numPredictValues.length + " attempts");
        }
    }

    /**
     * Шаг 2: Prompt Builder — из enriched JSON делает длинный текст-промпт для Flux.
     */
    private String buildFluxPrompt(String enrichedJson) throws Exception {
        log.info("=== [PROMPT BUILD] Step 2: Building Flux prompt from enriched JSON ===");
        log.info("[PROMPT BUILD] Input enriched JSON length: {} chars", enrichedJson.length());
        log.debug("[PROMPT BUILD] Input enriched JSON:\n{}", enrichedJson);
        
        // Логируем ключевые поля enriched JSON, которые будут использованы для промпта
        try {
            JsonNode enrichedNode = mapper.readTree(enrichedJson);
            if (enrichedNode.has("location")) {
                JsonNode locNode = enrichedNode.get("location");
                if (locNode.isObject() && locNode.has("description")) {
                    log.info("[PROMPT BUILD] Using location.description: '{}'", 
                            locNode.get("description").asText().length() > 100 
                            ? locNode.get("description").asText().substring(0, 100) + "..." 
                            : locNode.get("description").asText());
                }
            }
            if (enrichedNode.has("characters")) {
                JsonNode charsNode = enrichedNode.get("characters");
                if (charsNode.isArray()) {
                    log.info("[PROMPT BUILD] Using {} characters for prompt", charsNode.size());
                }
            }
            if (enrichedNode.has("camera")) {
                JsonNode cameraNode = enrichedNode.get("camera");
                if (cameraNode.isObject()) {
                    log.info("[PROMPT BUILD] Using camera settings for prompt");
                }
            }
            if (enrichedNode.has("lighting")) {
                JsonNode lightingNode = enrichedNode.get("lighting");
                if (lightingNode.isObject()) {
                    log.info("[PROMPT BUILD] Using lighting settings for prompt");
                }
            }
        } catch (Exception e) {
            log.warn("[PROMPT BUILD] Failed to parse enriched JSON for logging: {}", e.getMessage());
        }

        String fullPrompt = promptBuilderSystemPrompt
                + "\n\nHere is the enriched JSON:\n"
                + enrichedJson;
        
        log.info("[PROMPT BUILD] Calling LLM to generate Flux prompt (prompt length: {} chars)", fullPrompt.length());

        String raw = ollamaClient.generateText(fullPrompt).block();
        if (raw == null) {
            throw new IllegalStateException("LLM returned null response for Flux prompt");
        }

        log.debug("[PROMPT BUILD] LLM raw response length: {} chars (first 1000 chars): {}", 
                raw.length(), raw.length() > 1000 ? raw.substring(0, 1000) + "..." : raw);

        raw = cleanModelOutput(raw);
        String fluxPrompt = raw.trim();
        
        log.info("[PROMPT BUILD] ✓ Flux prompt generated successfully");
        log.info("[PROMPT BUILD] Flux prompt length: {} chars", fluxPrompt.length());
        log.info("[PROMPT BUILD] Flux prompt (first 500 chars): {}", 
                fluxPrompt.length() > 500 ? fluxPrompt.substring(0, 500) + "..." : fluxPrompt);
        log.info("[PROMPT BUILD] Flux prompt (last 200 chars): {}", 
                fluxPrompt.length() > 200 ? "..." + fluxPrompt.substring(fluxPrompt.length() - 200) : fluxPrompt);
        log.debug("[PROMPT BUILD] Full Flux prompt:\n{}", fluxPrompt);
        
        log.info("[PROMPT BUILD] === Step 2 completed: enriched JSON → Flux prompt ===");
        
        return fluxPrompt;
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