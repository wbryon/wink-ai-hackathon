package ru.wink.winkaipreviz.controller;

import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import ru.wink.winkaipreviz.dto.FrameCardDto;
import ru.wink.winkaipreviz.dto.FrameDto;
import ru.wink.winkaipreviz.dto.GenerateFrameRequest;
import ru.wink.winkaipreviz.dto.PromptSlotsDto;
import ru.wink.winkaipreviz.dto.RefineSceneRequest;
import ru.wink.winkaipreviz.dto.SceneDto;
import ru.wink.winkaipreviz.dto.SceneVisualDto;
import ru.wink.winkaipreviz.dto.UpdateSceneRequest;
import ru.wink.winkaipreviz.service.PrevizService;
import ru.wink.winkaipreviz.service.SceneVisualService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SceneController {

    private final PrevizService service;
    private final SceneVisualService sceneVisualService;

    public SceneController(PrevizService service, SceneVisualService sceneVisualService) {
        this.service = service;
        this.sceneVisualService = sceneVisualService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "backend");
    }

    @PutMapping("/scenes/{sceneId}")
    public SceneDto updateScene(@PathVariable String sceneId, @Valid @RequestBody UpdateSceneRequest req) {
        return service.updateScene(sceneId, req);
    }

    /**
     * Быстрая правка сцены коротким текстом.
     * На первом этапе просто дописывает текст в описание сцены.
     */
    @PostMapping("/scenes/{sceneId}/refine")
    public SceneDto refineScene(@PathVariable String sceneId, @Valid @RequestBody RefineSceneRequest req) {
        return service.refineScene(sceneId, req);
    }

    @DeleteMapping("/scenes/{sceneId}")
    public Map<String, Object> deleteScene(@PathVariable String sceneId, @RequestParam(value = "scriptId", required = false) String scriptId) {
        boolean ok = service.deleteScene(sceneId);
        return Map.of("success", ok);
    }

    @GetMapping("/scenes/{sceneId}/frames")
    public List<FrameDto> getFrames(@PathVariable String sceneId) {
        return service.getFrames(sceneId);
    }

    @PostMapping("/scenes/{sceneId}/generate")
    public FrameDto generate(@PathVariable String sceneId, @Valid @RequestBody GenerateFrameRequest req) {
        return service.generateFrame(sceneId, req);
    }

    /**
     * Генерация кадра через progressive path: Sketch → Mid → Final.
     * Если Sketch отсутствует, создает его сначала.
     * Затем последовательно создает Mid из Sketch и Final из Mid через img2img.
     * 
     * @param sceneId ID сцены
     * @param req запрос на генерацию (detailLevel может быть "mid" или "final", по умолчанию "final")
     * @return финальный кадр (Final или Mid, в зависимости от detailLevel)
     */
    @PostMapping("/scenes/{sceneId}/generate-progressive")
    public FrameDto generateProgressive(@PathVariable String sceneId, @Valid @RequestBody GenerateFrameRequest req) {
        return service.generateProgressiveFrame(sceneId, req);
    }

    /**
     * Полный список frame-card по всем кадрам сценария.
     */
    @GetMapping("/scripts/{scriptId}/frames/cards")
    public List<FrameCardDto> getFrameCards(@PathVariable String scriptId) {
        return service.getFrameCardsForScript(scriptId);
    }
    
    /**
     * Получить промпт-слоты для сцены.
     */
    @GetMapping("/scenes/{sceneId}/slots")
    public PromptSlotsDto getSlots(@PathVariable String sceneId) throws Exception {
        java.util.UUID id = java.util.UUID.fromString(sceneId);
        PromptSlotsDto slots = sceneVisualService.getSlotsForScene(id);
        if (slots == null) {
            throw new IllegalArgumentException("Slots not found for sceneId=" + sceneId + ". Generate enriched JSON first.");
        }
        return slots;
    }
    
    /**
     * Обновить промпт-слоты для сцены.
     * Пересобирает enriched JSON и Flux prompt из обновленных слотов.
     * Опционально обновляет связанную сущность Scene.
     * 
     * @param sceneId ID сцены
     * @param slots обновленные слоты
     * @param updateScene если true, обновляет поля Scene из слотов (по умолчанию false)
     * @return обновленный SceneVisualDto с новыми enriched JSON и Flux prompt
     */
    @PutMapping("/scenes/{sceneId}/slots")
    public SceneVisualDto updateSlots(
            @PathVariable String sceneId,
            @Valid @RequestBody PromptSlotsDto slots,
            @RequestParam(value = "updateScene", defaultValue = "false") boolean updateScene
    ) throws Exception {
        java.util.UUID id = java.util.UUID.fromString(sceneId);
        return sceneVisualService.updateSlotsForScene(id, slots, updateScene);
    }
}
