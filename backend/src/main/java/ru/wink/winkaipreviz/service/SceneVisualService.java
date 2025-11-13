package ru.wink.winkaipreviz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wink.winkaipreviz.dto.PromptSlotsDto;
import ru.wink.winkaipreviz.dto.SceneVisualDto;
import ru.wink.winkaipreviz.entity.SceneVisualEntity;
import ru.wink.winkaipreviz.entity.VisualStatus;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.repository.SceneVisualRepository;
import ru.wink.winkaipreviz.repository.SceneRepository;

@Service
public class SceneVisualService {

    private final SceneToFluxPromptService sceneToFluxPromptService;
    private final SceneVisualRepository sceneVisualRepository;
    private final SceneRepository sceneRepository;
    private final ObjectMapper mapper;

    public SceneVisualService(SceneToFluxPromptService sceneToFluxPromptService,
                              SceneVisualRepository sceneVisualRepository,
                              SceneRepository sceneRepository,
                              ObjectMapper mapper) {
        this.sceneToFluxPromptService = sceneToFluxPromptService;
        this.sceneVisualRepository = sceneVisualRepository;
        this.sceneRepository = sceneRepository;
        this.mapper = mapper;
    }

    /**
     * Атомарный пайплайн:
     * baseSceneJson (из первой модели) → Enriched JSON → Flux prompt →
     * сохранить enriched + prompt в БД → вернуть prompt на фронт.
     */
    @Transactional
    public SceneVisualDto generatePromptAndPersist(java.util.UUID sceneId, String baseSceneJson) throws Exception {
        // 1. Запускаем LLM-пайплайн (может кинуть исключение, тогда транзакция не начнётся/откатится)
        SceneToFluxPromptService.FluxPromptResult result =
                sceneToFluxPromptService.generateFromSceneJson(baseSceneJson);

        // 2. Находим существующую визуализацию сцены или создаём новую
        SceneVisualEntity entity = sceneVisualRepository.findBySceneId(sceneId)
                .orElseGet(SceneVisualEntity::new);

        entity.setSceneId(sceneId);
        entity.setEnrichedJson(result.enrichedJson());
        entity.setFluxPrompt(result.prompt());
        // imageUrl пока не знаем — заполнишь после генерации кадра
        entity.setStatus(VisualStatus.PROMPT_READY);

        SceneVisualEntity saved = sceneVisualRepository.save(entity);

        // 3. DTO для фронта
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public SceneVisualDto getBySceneId(java.util.UUID sceneId) throws Exception {
        SceneVisualEntity entity = sceneVisualRepository.findBySceneId(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene visual not found for sceneId=" + sceneId));
        return mapToDto(entity);
    }

    /**
     * Вернуть только слоты промпта для указанной сцены (или null, если визуализация ещё не создана).
     */
    @Transactional(readOnly = true)
    public PromptSlotsDto getSlotsForScene(java.util.UUID sceneId) throws Exception {
        SceneVisualEntity entity = sceneVisualRepository.findBySceneId(sceneId).orElse(null);
        if (entity == null || entity.getEnrichedJson() == null || entity.getEnrichedJson().isBlank()) {
            return null;
        }
        return buildSlotsFromEnrichedJson(entity.getEnrichedJson());
    }
    
    /**
     * Обновляет слоты промпта для сцены и пересобирает enriched JSON и Flux prompt.
     * Также обновляет связанную сущность Scene при необходимости.
     * 
     * @param sceneId ID сцены
     * @param slots обновленные слоты
     * @param updateScene если true, обновляет поля Scene из слотов
     * @return обновленный SceneVisualDto
     */
    @Transactional
    public SceneVisualDto updateSlotsForScene(java.util.UUID sceneId, PromptSlotsDto slots, boolean updateScene) throws Exception {
        // Получаем или создаем визуализацию сцены
        SceneVisualEntity entity = sceneVisualRepository.findBySceneId(sceneId)
                .orElseGet(() -> {
                    SceneVisualEntity newEntity = new SceneVisualEntity();
                    newEntity.setSceneId(sceneId);
                    return newEntity;
                });
        
        // Обновляем enriched JSON из слотов
        String currentEnrichedJson = entity.getEnrichedJson();
        String updatedEnrichedJson = updateEnrichedJsonFromSlots(
                currentEnrichedJson != null && !currentEnrichedJson.isBlank() ? currentEnrichedJson : "{}",
                slots
        );
        
        entity.setEnrichedJson(updatedEnrichedJson);
        
        // Пересобираем Flux prompt из обновленного enriched JSON
        String newFluxPrompt = sceneToFluxPromptService.buildFluxPromptFromEnrichedJson(updatedEnrichedJson);
        entity.setFluxPrompt(newFluxPrompt);
        entity.setStatus(VisualStatus.PROMPT_READY);
        
        SceneVisualEntity saved = sceneVisualRepository.save(entity);
        
        // Обновляем Scene, если требуется
        if (updateScene) {
            updateSceneFromSlots(sceneId, slots);
        }
        
        return mapToDto(saved);
    }
    
    /**
     * Обновляет enriched JSON из слотов промпта.
     */
    private String updateEnrichedJsonFromSlots(String currentEnrichedJson, PromptSlotsDto slots) throws Exception {
        JsonNode root = mapper.readTree(currentEnrichedJson);
        com.fasterxml.jackson.databind.node.ObjectNode objectNode = root.deepCopy();
        
        // Обновляем персонажей
        if (slots.characters() != null && !slots.characters().isEmpty()) {
            com.fasterxml.jackson.databind.node.ArrayNode charactersArray = mapper.createArrayNode();
            for (PromptSlotsDto.CharacterSlotDto ch : slots.characters()) {
                com.fasterxml.jackson.databind.node.ObjectNode charNode = mapper.createObjectNode();
                if (ch.name() != null) charNode.put("name", ch.name());
                if (ch.appearance() != null) charNode.put("appearance", ch.appearance());
                if (ch.clothing() != null && !ch.clothing().isEmpty()) {
                    com.fasterxml.jackson.databind.node.ArrayNode clothingArray = mapper.createArrayNode();
                    ch.clothing().forEach(clothingArray::add);
                    charNode.set("clothing", clothingArray);
                }
                if (ch.pose() != null) charNode.put("pose", ch.pose());
                if (ch.action() != null) charNode.put("action", ch.action());
                if (ch.positionInFrame() != null) charNode.put("position_in_frame", ch.positionInFrame());
                if (ch.emotion() != null) charNode.put("emotion", ch.emotion());
                charactersArray.add(charNode);
            }
            objectNode.set("characters", charactersArray);
        }
        
        // Обновляем локацию
        if (slots.location() != null) {
            com.fasterxml.jackson.databind.node.ObjectNode locationNode = mapper.createObjectNode();
            if (slots.location().raw() != null) locationNode.put("raw", slots.location().raw());
            if (slots.location().normalized() != null) locationNode.put("norm", slots.location().normalized());
            if (slots.location().description() != null) locationNode.put("description", slots.location().description());
            if (slots.location().environmentDetails() != null && !slots.location().environmentDetails().isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode envDetailsArray = mapper.createArrayNode();
                slots.location().environmentDetails().forEach(envDetailsArray::add);
                locationNode.set("environment_details", envDetailsArray);
            }
            objectNode.set("location", locationNode);
            
            // Обновляем время суток
            if (slots.location().time() != null) {
                com.fasterxml.jackson.databind.node.ObjectNode timeNode = mapper.createObjectNode();
                if (slots.location().time().raw() != null) timeNode.put("raw", slots.location().time().raw());
                if (slots.location().time().normalized() != null) timeNode.put("norm", slots.location().time().normalized());
                if (slots.location().time().description() != null) timeNode.put("description", slots.location().time().description());
                objectNode.set("time", timeNode);
            }
        }
        
        // Обновляем реквизиты
        if (slots.action() != null && slots.action().props() != null && !slots.action().props().isEmpty()) {
            com.fasterxml.jackson.databind.node.ArrayNode propsArray = mapper.createArrayNode();
            for (PromptSlotsDto.PropSlotDto prop : slots.action().props()) {
                com.fasterxml.jackson.databind.node.ObjectNode propNode = mapper.createObjectNode();
                propNode.put("name", prop.name());
                propNode.put("required", prop.required());
                if (prop.owner() != null) propNode.put("owner", prop.owner());
                propsArray.add(propNode);
            }
            objectNode.set("props", propsArray);
        }
        
        // Обновляем композицию
        if (slots.composition() != null) {
            com.fasterxml.jackson.databind.node.ObjectNode cameraNode = mapper.createObjectNode();
            if (slots.composition().shotType() != null) cameraNode.put("shot_type", slots.composition().shotType());
            if (slots.composition().cameraAngle() != null) cameraNode.put("angle", slots.composition().cameraAngle());
            if (slots.composition().framing() != null) cameraNode.put("framing", slots.composition().framing());
            if (slots.composition().motion() != null) cameraNode.put("motion", slots.composition().motion());
            objectNode.set("camera", cameraNode);
            
            // Обновляем локационные подсказки
            if (slots.composition().locationalCues() != null && !slots.composition().locationalCues().isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode cuesArray = mapper.createArrayNode();
                slots.composition().locationalCues().forEach(cuesArray::add);
                objectNode.set("locational_cues", cuesArray);
            }
        }
        
        // Обновляем тон
        if (slots.tone() != null && !slots.tone().isEmpty()) {
            com.fasterxml.jackson.databind.node.ArrayNode toneArray = mapper.createArrayNode();
            slots.tone().forEach(toneArray::add);
            objectNode.set("mood", toneArray);
        }
        
        // Обновляем стиль
        if (slots.styleHints() != null && !slots.styleHints().isEmpty()) {
            com.fasterxml.jackson.databind.node.ArrayNode styleArray = mapper.createArrayNode();
            slots.styleHints().forEach(styleArray::add);
            objectNode.set("style_hints", styleArray);
        }
        
        // Обновляем негативы
        if (slots.negatives() != null) {
            com.fasterxml.jackson.databind.node.ObjectNode negativesNode = mapper.createObjectNode();
            if (slots.negatives().global() != null && !slots.negatives().global().isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode globalArray = mapper.createArrayNode();
                slots.negatives().global().forEach(globalArray::add);
                negativesNode.set("global", globalArray);
            }
            if (slots.negatives().sceneSpecific() != null && !slots.negatives().sceneSpecific().isEmpty()) {
                com.fasterxml.jackson.databind.node.ArrayNode sceneArray = mapper.createArrayNode();
                slots.negatives().sceneSpecific().forEach(sceneArray::add);
                negativesNode.set("scene_specific", sceneArray);
            }
            objectNode.set("negative_prompts", negativesNode);
        }
        
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectNode);
    }
    
    /**
     * Обновляет сущность Scene из слотов промпта (двусторонняя связка).
     */
    private void updateSceneFromSlots(java.util.UUID sceneId, PromptSlotsDto slots) {
        Scene scene = sceneRepository.findById(sceneId).orElse(null);
        if (scene == null) {
            return;
        }
        
        // Обновляем локацию
        if (slots.location() != null && slots.location().raw() != null) {
            scene.setLocation(slots.location().raw());
        }
        
        // Обновляем персонажей
        if (slots.characters() != null && !slots.characters().isEmpty()) {
            java.util.List<String> characterNames = new java.util.ArrayList<>();
            for (PromptSlotsDto.CharacterSlotDto ch : slots.characters()) {
                if (ch.name() != null && !ch.name().isBlank()) {
                    characterNames.add(ch.name());
                }
            }
            scene.setCharacters(characterNames);
        }
        
        // Обновляем реквизиты
        if (slots.action() != null && slots.action().props() != null && !slots.action().props().isEmpty()) {
            java.util.List<String> propNames = new java.util.ArrayList<>();
            for (PromptSlotsDto.PropSlotDto prop : slots.action().props()) {
                if (prop.name() != null && !prop.name().isBlank()) {
                    propNames.add(prop.name());
                }
            }
            scene.setProps(propNames);
        }
        
        // Обновляем тон (берем первый элемент массива для обратной совместимости)
        if (slots.tone() != null && !slots.tone().isEmpty()) {
            scene.setTone(slots.tone().get(0));
        }
        
        // Обновляем стиль (берем первый элемент массива для обратной совместимости)
        if (slots.styleHints() != null && !slots.styleHints().isEmpty()) {
            scene.setStyle(slots.styleHints().get(0));
        }
        
        sceneRepository.save(scene);
    }

    private SceneVisualDto mapToDto(SceneVisualEntity saved) throws Exception {
        PromptSlotsDto slots = null;
        if (saved.getEnrichedJson() != null && !saved.getEnrichedJson().isBlank()) {
            slots = buildSlotsFromEnrichedJson(saved.getEnrichedJson());
        }

        return new SceneVisualDto(
                saved.getId(),
                saved.getSceneId(),
                saved.getEnrichedJson(),
                saved.getFluxPrompt(),
                saved.getImageUrl(),
                saved.getStatus().name(),
                slots
        );
    }

    /**
     * Строим человекочитаемые слоты промпта на основе enriched JSON
     * по спецификации SceneVisualSpec_v1.
     */
    private PromptSlotsDto buildSlotsFromEnrichedJson(String enrichedJson) throws Exception {
        JsonNode root = mapper.readTree(enrichedJson);

        // КТО: персонажи с атрибутами
        java.util.List<PromptSlotsDto.CharacterSlotDto> characters = new java.util.ArrayList<>();
        JsonNode charactersNode = root.path("characters");
        if (charactersNode.isArray()) {
            for (JsonNode ch : charactersNode) {
                String name = ch.path("name").asText(null);
                String appearance = ch.path("appearance").asText(null);
                java.util.List<String> clothing = new java.util.ArrayList<>();
                JsonNode clothingArr = ch.path("clothing");
                if (clothingArr.isArray()) {
                    for (JsonNode c : clothingArr) {
                        String item = c.asText(null);
                        if (item != null && !item.isBlank()) {
                            clothing.add(item);
                        }
                    }
                }
                String pose = ch.path("pose").asText(null);
                String action = ch.path("action").asText(null);
                String position = ch.path("position_in_frame").asText(null);
                String emotion = ch.path("emotion").asText(null);

                characters.add(new PromptSlotsDto.CharacterSlotDto(
                        name, appearance, clothing, pose, action, position, emotion
                ));
            }
        }

        // ГДЕ: локация, INT/EXT, время суток
        JsonNode locationNode = root.path("location");
        JsonNode timeNode = root.path("time");
        
        java.util.List<String> environmentDetails = new java.util.ArrayList<>();
        JsonNode envDetails = locationNode.path("environment_details");
        if (envDetails.isArray()) {
            for (JsonNode n : envDetails) {
                String detail = n.asText(null);
                if (detail != null && !detail.isBlank()) {
                    environmentDetails.add(detail);
                }
            }
        }
        
        // Определяем INT/EXT из slugline_raw или location
        String sceneType = null;
        JsonNode sluglineRaw = root.path("slugline_raw");
        if (sluglineRaw.isTextual()) {
            String slugline = sluglineRaw.asText("").toUpperCase();
            if (slugline.contains("ИНТ") || slugline.contains("INT")) {
                sceneType = "INT";
            } else if (slugline.contains("ЭКСТ") || slugline.contains("EXT")) {
                sceneType = "EXT";
            }
        }
        
        PromptSlotsDto.TimeSlotDto timeSlot = new PromptSlotsDto.TimeSlotDto(
                timeNode.path("raw").asText(null),
                timeNode.path("norm").asText(null),
                timeNode.path("description").asText(null)
        );
        
        PromptSlotsDto.LocationSlotDto locationSlot = new PromptSlotsDto.LocationSlotDto(
                locationNode.path("raw").asText(null),
                locationNode.path("norm").asText(null),
                locationNode.path("description").asText(null),
                environmentDetails,
                sceneType,
                timeSlot
        );

        // ЧТО: действие и реквизиты
        String mainAction = null; // можно извлечь из text_excerpt или description
        JsonNode textExcerpt = root.path("text_excerpt");
        if (textExcerpt.isTextual() && !textExcerpt.asText("").isBlank()) {
            mainAction = textExcerpt.asText(null);
        }
        
        java.util.List<PromptSlotsDto.PropSlotDto> props = new java.util.ArrayList<>();
        JsonNode propsNode = root.path("props");
        if (propsNode.isArray()) {
            for (JsonNode prop : propsNode) {
                String propName = prop.path("name").asText(null);
                boolean required = prop.path("required").asBoolean(false);
                String owner = prop.path("owner").asText(null);
                if (propName != null && !propName.isBlank()) {
                    props.add(new PromptSlotsDto.PropSlotDto(propName, required, owner));
                }
            }
        }
        
        PromptSlotsDto.ActionSlotDto actionSlot = new PromptSlotsDto.ActionSlotDto(mainAction, props);

        // КОМПОЗИЦИЯ: размер кадра, угол камеры, локационные подсказки
        JsonNode camera = root.path("camera");
        java.util.List<String> locationalCues = new java.util.ArrayList<>();
        JsonNode locationalCuesNode = root.path("locational_cues");
        if (locationalCuesNode.isArray()) {
            for (JsonNode cue : locationalCuesNode) {
                String cueText = cue.asText(null);
                if (cueText != null && !cueText.isBlank()) {
                    locationalCues.add(cueText);
                }
            }
        }
        
        PromptSlotsDto.CompositionSlotDto compositionSlot = new PromptSlotsDto.CompositionSlotDto(
                camera.path("shot_type").asText(null),
                camera.path("angle").asText(null),
                camera.path("framing").asText(null),
                camera.path("motion").asText(null),
                locationalCues
        );

        // ТОН: эмоциональная окраска (массив)
        java.util.List<String> tone = new java.util.ArrayList<>();
        JsonNode moodArr = root.path("mood");
        if (moodArr.isArray()) {
            for (JsonNode m : moodArr) {
                String moodItem = m.asText(null);
                if (moodItem != null && !moodItem.isBlank()) {
                    tone.add(moodItem);
                }
            }
        }

        // СТИЛЬ: стилистические подсказки (массив)
        java.util.List<String> styleHints = new java.util.ArrayList<>();
        JsonNode styleHintsNode = root.path("style_hints");
        if (styleHintsNode.isArray()) {
            for (JsonNode s : styleHintsNode) {
                String styleItem = s.asText(null);
                if (styleItem != null && !styleItem.isBlank()) {
                    styleHints.add(styleItem);
                }
            }
        }

        // Негативы: глобальные и сценовые
        java.util.List<String> globalNegatives = new java.util.ArrayList<>();
        java.util.List<String> sceneNegatives = new java.util.ArrayList<>();
        JsonNode negativesNode = root.path("negative_prompts");
        if (negativesNode.isObject()) {
            JsonNode globalNode = negativesNode.path("global");
            if (globalNode.isArray()) {
                for (JsonNode n : globalNode) {
                    String neg = n.asText(null);
                    if (neg != null && !neg.isBlank()) {
                        globalNegatives.add(neg);
                    }
                }
            }
            JsonNode sceneNode = negativesNode.path("scene_specific");
            if (sceneNode.isArray()) {
                for (JsonNode n : sceneNode) {
                    String neg = n.asText(null);
                    if (neg != null && !neg.isBlank()) {
                        sceneNegatives.add(neg);
                    }
                }
            }
        }
        
        PromptSlotsDto.NegativePromptSlotDto negativesSlot = new PromptSlotsDto.NegativePromptSlotDto(
                globalNegatives, sceneNegatives
        );

        // Освещение (legacy)
        JsonNode lightingNode = root.path("lighting");
        StringBuilder lighting = new StringBuilder();
        appendIfNotBlank(lighting, lightingNode.path("type").asText(null));
        appendIfNotBlank(lighting, lightingNode.path("description").asText(null));

        // Технические подсказки (legacy)
        String technical = "";

        return new PromptSlotsDto(
                characters.isEmpty() ? null : characters,
                locationSlot,
                actionSlot,
                compositionSlot,
                tone.isEmpty() ? null : tone,
                styleHints.isEmpty() ? null : styleHints,
                negativesSlot,
                lighting.isEmpty() ? null : lighting.toString(),
                technical
        );
    }

    private static void appendIfNotBlank(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(". ");
        }
        sb.append(value.trim());
    }
}

