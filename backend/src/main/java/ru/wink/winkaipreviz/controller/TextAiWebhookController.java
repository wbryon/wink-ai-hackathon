package ru.wink.winkaipreviz.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import ru.wink.winkaipreviz.dto.SceneDto;
import ru.wink.winkaipreviz.dto.ScenesWebhookRequest;
import ru.wink.winkaipreviz.service.PrevizService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/text-ai")
public class TextAiWebhookController {

    private final PrevizService service;

    public TextAiWebhookController(PrevizService service) {
        this.service = service;
    }

    @PostMapping(path = "/scenes", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> acceptScenes(@Valid @RequestBody ScenesWebhookRequest req) {
        List<SceneDto> saved = service.ingestScenes(req.getScriptId(), req.getScenes());
        int requested = req.getScenes() == null ? 0 : req.getScenes().size();
        int accepted = saved.size();
        int skipped = Math.max(0, requested - accepted);
        return Map.of(
                "accepted", accepted,
                "skipped", skipped,
                "scenes", saved
        );
    }
}


