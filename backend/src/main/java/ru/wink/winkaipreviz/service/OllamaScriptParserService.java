package ru.wink.winkaipreviz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.entity.SceneStatus;

import java.util.ArrayList;
import java.util.List;

@Service
public class OllamaScriptParserService {

    private static final Logger log = LoggerFactory.getLogger(OllamaScriptParserService.class);

    private final OllamaClient ollamaClient;
    private final ObjectMapper mapper = new ObjectMapper();

    // System-промпт для LLM: просим ВЫДАВАТЬ ТОЛЬКО JSON-МАССИВ СЦЕН
    private static final String SCENE_PARSER_PROMPT_TEMPLATE = """
You are not a critic and not an analyst.
You are a STRICT JSON CONVERTER for screenplay text.

YOUR ONLY JOB
- Input: a fragment (chunk) of a Russian-language film screenplay.
- Output: a JSON ARRAY of scene objects, each describing exactly ONE scene.
- You MUST NOT output summaries, reviews, themes, analysis, or free-form text.
- You MUST NOT invent any keys other than those in the schema below.

ABOUT THE INPUT
The chunk may contain:
- one or several full scenes;
- the beginning of a scene (continued in the next chunk);
- the end of a scene (started in a previous chunk);
- or fragments of dialogue/action without a clear slugline.

SCENE BOUNDARIES (CRITICAL)
A NEW SCENE starts when you see a SLUGLINE. In this screenplay, a slugline is typically:
- A line starting with:
    "INT.", "EXT.", "INT/EXT",
    "ИНТ.", "НАТ.", "ИНТ./НАТ.", "НАТ./ИНТ.",
    "EXT/INT", "INT./EXT."
- OR a numbered slugline with number + dot, optionally with a letter suffix, for example:
    "1-1.ИНТ./НАТ. ШКОЛА №6. ЛЕСТНИЦА. НОЧЬ."
    "1-1-А.ИНТ./НАТ. ШКОЛА №6. КОРИДОР/ХОЛЛ. НОЧЬ."
    "1-2А.НАТ.ПРОУЛОК.НОЧЬ."
    "1-2.ИНТ.ШКОЛА №6.СПОРТЗАЛ.НОЧЬ."
    "1-3.НАТ. У КОМЕНДАТУРЫ (ЦЕНТРАЛЬНАЯ ПЛОЩАДЬ). НОЧЬ."
    "1-4.НАТ./ ИНТ.ШКОЛА №6.СПОРТЗАЛ.УТРО."
- OR a line in ALL CAPS in Russian that describes LOCATION and TIME OF DAY.

ALGORITHM (YOU MUST FOLLOW THIS MECHANICALLY)
1) In your internal reasoning, scan the chunk line by line and collect ALL sluglines in order.
2) For EACH slugline, define a scene as:
   - from this slugline
   - up to (but not including) the next slugline
   - or end of chunk.
3) Let N be the number of detected scenes (including partial ones).
4) You MUST output EXACTLY N scene objects in the JSON array.
5) Each scene object MUST correspond to exactly ONE slugline (one logical scene).
6) NEVER merge multiple sluglines into one scene object.
7) NEVER skip a slugline.

IMPORTANT CONSTRAINTS
- The screenplay is in Russian, but field names in JSON must be in English (as in the schema).
- If the chunk starts in the middle of a scene or ends in the middle of a scene,
  you may still create a scene object but set "is_partial": true.
- Do NOT hallucinate extra scenes that are not in the text.

STRICT SCHEMA (ONLY THESE FIELDS ARE ALLOWED)
The TOP-LEVEL value MUST be a JSON ARRAY: [ {scene1}, {scene2}, ... ].

Each element MUST be an object with ONLY these fields:
- "scene_id"        : string
- "slugline_raw"    : string
- "type"            : string or null      // "INT", "EXT", "INT/EXT", etc.
- "location"        : { "raw": string, "norm": string }
- "time"            : { "raw": string, "norm": string }
- "characters"      : array of { "name": string, "norm": string }
- "props"           : array of { "name": string, "required": boolean }
- "locational_cues" : array of strings
- "tone"            : array of strings
- "style_hints"     : array of strings
- "text_excerpt"    : string
- "is_partial"      : boolean

You MUST NOT output any extra top-level fields like:
- "title", "setting", "summary", "plot", "themes", "analysis", etc.
You MUST NOT wrap the array into an outer object like {"scenes":[...]}.
You MUST ONLY output a bare array: [ { ... }, { ... }, ... ].

If you are tempted to output:
{
  "title": "...",
  "setting": "...",
  "characters": [ ... ],
  "plot": [ ... ]
}
DO NOT DO THIS. INSTEAD, you MUST output an ARRAY of scene objects using the schema above.

EXAMPLE OF ONE SCENE OBJECT (for illustration of structure ONLY):

[
  {
    "scene_id": "S_001",
    "slugline_raw": "ИНТ. КАФЕ У ОКНА — ВЕЧЕР",
    "type": "INT",
    "location": {
      "raw": "КАФЕ У ОКНА",
      "norm": "CAFE_WINDOW"
    },
    "time": {
      "raw": "ВЕЧЕР",
      "norm": "EVENING"
    },
    "characters": [
      {
        "name": "МАША",
        "norm": "MASHA"
      }
    ],
    "props": [
      {
        "name": "папка",
        "required": true
      }
    ],
    "locational_cues": ["у окна"],
    "tone": ["уютный", "вечерний"],
    "style_hints": ["нуар"],
    "text_excerpt": "За окном дождь...",
    "is_partial": false
  }
]

OUTPUT FORMAT (CRITICAL)
- Return ONLY a valid JSON ARRAY.
- NO comments, NO explanations, NO markdown, NO <think> tags.
- The FIRST character of your reply MUST be '['.
- The LAST character of your reply MUST be ']'.
- If there are no scenes, return [].

NOW READ THE FOLLOWING CHUNK AND RETURN THE JSON ARRAY OF SCENES.

CHUNK TEXT (RUSSIAN SCREENPLAY):
---
{{CHUNK_TEXT_HERE}}
---
""";

    public OllamaScriptParserService(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    /**
     * Парсит текст одной сцены в base scene JSON.
     * Используется для пайплайна обогащения: scene text -> JSON -> enriched JSON -> prompt.
     */
    public String parseSceneTextToJson(String sceneText) throws Exception {
        // Используем тот же промпт, но ожидаем один объект сцены, а не массив
        String prompt = SCENE_PARSER_PROMPT_TEMPLATE
                .replace("{{CHUNK_TEXT_HERE}}", sceneText)
                + "\n\nIMPORTANT: Since this is a single scene (not a chunk), return an array with exactly ONE scene object.";

        String raw = ollamaClient.generateJson(prompt).block();

        if (raw == null) {
            log.warn("LLM returned null response for scene text");
            throw new IllegalStateException("Failed to parse scene text to JSON");
        }

        raw = cleanModelOutput(raw);
        if (raw.isBlank()) {
            throw new IllegalStateException("LLM returned empty response for scene text");
        }

        // Извлекаем JSON массив
        String candidate = raw.trim();
        int start = candidate.indexOf('[');
        int end = candidate.lastIndexOf(']');

        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalStateException("LLM did not return a JSON array");
        }

        candidate = candidate.substring(start, end + 1).trim();

        JsonNode root = mapper.readTree(candidate);
        if (!root.isArray() || root.size() == 0) {
            throw new IllegalStateException("LLM did not return an array with at least one scene");
        }

        // Возвращаем первый элемент массива как JSON строку
        String baseJson = mapper.writeValueAsString(root.get(0));
        
        log.info("Parsed scene text to base JSON successfully");
        log.debug("Base JSON:\n{}", baseJson);
        
        // Логируем ключевые поля для быстрой диагностики
        try {
            JsonNode sceneNode = root.get(0);
            if (sceneNode.has("slugline_raw")) {
                log.info("Parsed slugline_raw: '{}'", sceneNode.get("slugline_raw").asText());
            }
            if (sceneNode.has("location")) {
                JsonNode locationNode = sceneNode.get("location");
                if (locationNode.isObject() && locationNode.has("raw")) {
                    log.info("Parsed location.raw: '{}'", locationNode.get("raw").asText());
                }
            }
            if (sceneNode.has("characters")) {
                JsonNode charsNode = sceneNode.get("characters");
                if (charsNode.isArray()) {
                    log.info("Parsed characters count: {}", charsNode.size());
                    for (int i = 0; i < Math.min(charsNode.size(), 3); i++) {
                        JsonNode charNode = charsNode.get(i);
                        String charName = charNode.has("name") ? charNode.get("name").asText() : "N/A";
                        log.debug("  Character {}: name='{}'", i + 1, charName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse base JSON for diagnostic logging: {}", e.getMessage());
        }
        
        return baseJson;
    }

    /**
     * Парсит один чанк сценария в СПИСОК base scene JSON (по одной строке на сцену).
     * Эти строки дальше можно сразу отдавать в Enricher.
     */
    public List<String> parseChunkToSceneJson(String chunkText) throws Exception {
        String prompt = SCENE_PARSER_PROMPT_TEMPLATE.replace("{{CHUNK_TEXT_HERE}}", chunkText);

        // Используем строгий JSON-режим (format=json, think=false)
        String raw = ollamaClient.generateJson(prompt).block();

        if (raw == null) {
            log.warn("LLM returned null response for chunk");
            return List.of();
        }

        String rawPreview = raw.length() > 2000 ? raw.substring(0, 2000) + "...[truncated]" : raw;
        log.info("LLM raw response for chunk:\n{}", rawPreview);

        // Удаляем <think>...</think> на случай, если модель всё-таки что-то туда сунула
        raw = cleanModelOutput(raw);

        if (raw.isBlank()) {
            log.warn("LLM returned empty response for chunk after cleaning");
            return List.of();
        }

        // Некоторые модели всё равно могут добавить текст вокруг JSON-массива.
        String candidate = raw.trim();
        int start = candidate.indexOf('[');
        int end = candidate.lastIndexOf(']');

        if (start == -1 || end == -1 || end <= start) {
            log.error("LLM did not return a JSON array. Cleaned response starts with: {}",
                    candidate.substring(0, Math.min(200, candidate.length())));
            return List.of();
        }

        if (start != 0 || end != candidate.length() - 1) {
            log.warn("LLM returned extra text around JSON, trimming to array segment");
            candidate = candidate.substring(start, end + 1).trim();
        }

        String candidatePreview = candidate.length() > 2000
                ? candidate.substring(0, 2000) + "...[truncated]"
                : candidate;
        log.info("LLM cleaned JSON candidate for chunk:\n{}", candidatePreview);

        JsonNode root;
        try {
            root = mapper.readTree(candidate);
        } catch (Exception e) {
            log.error("Failed to parse LLM response as JSON array. Candidate starts with: {}",
                    candidate.substring(0, Math.min(200, candidate.length())), e);
            return List.of();
        }

        if (!root.isArray()) {
            throw new IllegalStateException("LLM did not return an array of scenes");
        }

        List<String> scenesJson = new ArrayList<>();
        for (JsonNode node : root) {
            // Нормализуем JSON (убираем форматирование, лишние пробелы и т.п.)
            scenesJson.add(mapper.writeValueAsString(node));
        }

        return scenesJson;
    }

    /**
     * Парсит один чанк сценария в список Scene объектов.
     * Использует parseChunkToSceneJson и конвертирует JSON в Scene.
     */
    public List<Scene> parseChunk(String chunkText) throws Exception {
        List<String> scenesJson = parseChunkToSceneJson(chunkText);
        List<Scene> scenes = new ArrayList<>();

        for (String sceneJson : scenesJson) {
            try {
                JsonNode sceneNode = mapper.readTree(sceneJson);
                Scene scene = mapJsonToScene(sceneNode);
                scene.setStatus(SceneStatus.PARSED);
                scene.setOriginalJson(sceneJson); // Сохраняем исходный JSON
                scenes.add(scene);
            } catch (Exception e) {
                log.warn("Failed to parse scene JSON: {}", e.getMessage());
            }
        }

        return scenes;
    }

    private Scene mapJsonToScene(JsonNode node) {
        Scene scene = new Scene();
        applySceneJsonToEntity(node, scene);
        return scene;
    }

    /**
     * Applies parsed scene JSON (string form) to an existing Scene entity.
     * Used by background workers that already have a persisted Scene.
     */
    public void applySceneJsonToEntity(String sceneJson, Scene scene) throws Exception {
        JsonNode node = mapper.readTree(sceneJson);
        applySceneJsonToEntity(node, scene);
    }

    /**
     * Shared mapping logic: JSON node -> Scene fields.
     */
    private void applySceneJsonToEntity(JsonNode node, Scene scene) {
        // slugline_raw -> title
        if (node.has("slugline_raw")) {
            scene.setTitle(node.get("slugline_raw").asText());
        }

        // location.raw -> location
        if (node.has("location")) {
            JsonNode locationNode = node.get("location");
            if (locationNode.isObject() && locationNode.has("raw")) {
                scene.setLocation(locationNode.get("raw").asText());
            } else if (locationNode.isTextual()) {
                scene.setLocation(locationNode.asText());
            }
        }

        // description / text_excerpt -> description
        if (node.has("description")) {
            scene.setDescription(node.get("description").asText());
        }
        if (node.has("text_excerpt")) {
            // text_excerpt важнее — это как раз "визуальный" текст
            scene.setDescription(node.get("text_excerpt").asText());
        }

        // tone (array или строка) -> первый элемент / строка
        if (node.has("tone")) {
            JsonNode toneNode = node.get("tone");
            if (toneNode.isArray() && toneNode.size() > 0) {
                scene.setTone(toneNode.get(0).asText());
            } else if (toneNode.isTextual()) {
                scene.setTone(toneNode.asText());
            }
        }

        // style_hints (array или строка)
        if (node.has("style_hints")) {
            JsonNode styleNode = node.get("style_hints");
            if (styleNode.isArray() && styleNode.size() > 0) {
                scene.setStyle(styleNode.get(0).asText());
            } else if (styleNode.isTextual()) {
                scene.setStyle(styleNode.asText());
            }
        }

        // Персонажи
        if (node.has("characters")) {
            JsonNode charsNode = node.get("characters");
            List<String> characters = new ArrayList<>();
            if (charsNode.isArray()) {
                for (JsonNode charNode : charsNode) {
                    if (charNode.isObject() && charNode.has("name")) {
                        characters.add(charNode.get("name").asText());
                    } else if (charNode.isTextual()) {
                        characters.add(charNode.asText());
                    }
                }
            }
            scene.setCharacters(characters);
        }

        // Реквизит
        if (node.has("props")) {
            JsonNode propsNode = node.get("props");
            List<String> props = new ArrayList<>();
            if (propsNode.isArray()) {
                for (JsonNode propNode : propsNode) {
                    if (propNode.isObject() && propNode.has("name")) {
                        props.add(propNode.get("name").asText());
                    } else if (propNode.isTextual()) {
                        props.add(propNode.asText());
                    }
                }
            }
            scene.setProps(props);
        }

        // Обновляем статус на PARSED, если он ещё не установлен
        scene.setStatus(SceneStatus.PARSED);
    }

    /**
     * Удаляем блоки вида <think>...</think> из ответа модели.
     */
    private String cleanModelOutput(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
    }
}
