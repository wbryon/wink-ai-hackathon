package ru.wink.winkaipreviz.controller;

import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import ru.wink.winkaipreviz.dto.FrameDto;
import ru.wink.winkaipreviz.dto.GenerateFrameRequest;
import ru.wink.winkaipreviz.dto.SceneDto;
import ru.wink.winkaipreviz.dto.UpdateSceneRequest;
import ru.wink.winkaipreviz.service.PrevizService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SceneController {

    private final PrevizService service;

    public SceneController(PrevizService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "backend");
    }

    @PutMapping("/scenes/{sceneId}")
    public SceneDto updateScene(@PathVariable String sceneId, @Valid @RequestBody UpdateSceneRequest req) {
        return service.updateScene(sceneId, req);
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
}
