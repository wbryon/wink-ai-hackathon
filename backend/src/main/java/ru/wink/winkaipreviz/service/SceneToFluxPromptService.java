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
        log.info("--- Step 1: Enhancing scene JSON ---");
        log.debug("Input base JSON:\n{}", sceneJson);

        String fullPrompt = enhancerSystemPrompt
                + "\n\nHere is the scene JSON:\n"
                + sceneJson;

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
                
                log.info("Enriched JSON generated successfully on attempt {}", attempt + 1);
                log.info("Enriched JSON (first 1500 chars): {}", 
                        enrichedJson.length() > 1500 ? enrichedJson.substring(0, 1500) + "..." : enrichedJson);
                log.debug("Full enriched JSON:\n{}", enrichedJson);
                
                // Логируем ключевые поля для быстрой диагностики
                try {
                    JsonNode enrichedNode = mapper.readTree(enrichedJson);
                    
                    // Логируем slugline_raw, если есть
                    if (enrichedNode.has("slugline_raw")) {
                        log.info("Enriched slugline_raw: '{}'", enrichedNode.get("slugline_raw").asText());
                    }
                    
                    if (enrichedNode.has("location")) {
                        JsonNode locationNode = enrichedNode.get("location");
                        if (locationNode.isObject()) {
                            String locationRaw = locationNode.has("raw") ? locationNode.get("raw").asText() : "N/A";
                            String locationNorm = locationNode.has("norm") ? locationNode.get("norm").asText() : "N/A";
                            String locationDesc = locationNode.has("description") ? locationNode.get("description").asText() : "N/A";
                            log.info("Enriched location: raw='{}', norm='{}', description='{}'", 
                                    locationRaw, locationNorm, 
                                    locationDesc.length() > 100 ? locationDesc.substring(0, 100) + "..." : locationDesc);
                        }
                    }
                    if (enrichedNode.has("characters")) {
                        JsonNode charsNode = enrichedNode.get("characters");
                        if (charsNode.isArray()) {
                            log.info("Enriched characters count: {}", charsNode.size());
                            for (int i = 0; i < Math.min(charsNode.size(), 5); i++) {
                                JsonNode charNode = charsNode.get(i);
                                String charName = charNode.has("name") ? charNode.get("name").asText() : "N/A";
                                log.info("  Character {}: name='{}'", i + 1, charName);
                            }
                        } else {
                            log.warn("Enriched JSON has 'characters' field but it's not an array: {}", charsNode.getNodeType());
                        }
                    } else {
                        log.warn("Enriched JSON does not have 'characters' field");
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse enriched JSON for diagnostic logging: {}", e.getMessage(), e);
                }
                
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
        log.info("--- Step 2: Building Flux prompt from enriched JSON ---");
        log.debug("Input enriched JSON:\n{}", enrichedJson);

        String fullPrompt = promptBuilderSystemPrompt
                + "\n\nHere is the enriched JSON:\n"
                + enrichedJson;

        String raw = ollamaClient.generateText(fullPrompt).block();
        if (raw == null) {
            throw new IllegalStateException("LLM returned null response for Flux prompt");
        }

        log.debug("LLM raw response (first 1000 chars): {}", 
                raw.length() > 1000 ? raw.substring(0, 1000) + "..." : raw);

        raw = cleanModelOutput(raw);
        String fluxPrompt = raw.trim();
        
        log.info("Flux prompt generated successfully (length: {} chars)", fluxPrompt.length());
        log.info("Final Flux prompt:\n{}", fluxPrompt);
        
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