package ru.wink.winkaipreviz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import ru.wink.winkaipreviz.dto.SceneVisualDto;
import ru.wink.winkaipreviz.service.SceneToFluxPromptService;
import ru.wink.winkaipreviz.service.SceneVisualService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/visual")
public class SceneVisualController {

    private final SceneToFluxPromptService visualService;
    private final SceneVisualService sceneVisualService;
    private final ObjectMapper mapper;

    public SceneVisualController(SceneToFluxPromptService visualService,
                                 SceneVisualService sceneVisualService,
                                 ObjectMapper mapper) {
        this.visualService = visualService;
        this.sceneVisualService = sceneVisualService;
        this.mapper = mapper;
    }

    @PostMapping(
            path = "/prompt",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> buildPrompt(@RequestBody Map<String, Object> body) throws Exception {
        // base scene JSON может прийти в виде строки или объекта
        Object sceneObj = body.get("scene");
        String sceneJson;

        if (sceneObj instanceof String s) {
            sceneJson = s;
        } else {
            sceneJson = mapper.writeValueAsString(sceneObj);
        }

        // Здесь происходит весь пайплайн:
        // base JSON → LLM (Enricher) → LLM (Prompt Builder)
        SceneToFluxPromptService.FluxPromptResult result =
                visualService.generateFromSceneJson(sceneJson);

        return Map.of(
                "scene_id", result.sceneId(),
                "enriched_json", mapper.readTree(result.enrichedJson()),
                "prompt", result.prompt()
        );
    }

    /**
     * Получить enriched JSON, flux-промпт и вычисленные слоты для сцены.
     */
    @GetMapping(path = "/scenes/{sceneId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SceneVisualDto getVisual(@PathVariable String sceneId) throws Exception {
        UUID id = UUID.fromString(sceneId);
        return sceneVisualService.getBySceneId(id);
    }
}
