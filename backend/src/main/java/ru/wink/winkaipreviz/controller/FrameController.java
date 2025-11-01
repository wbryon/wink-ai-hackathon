package ru.wink.winkaipreviz.controller;

import org.springframework.web.bind.annotation.*;
import ru.wink.winkaipreviz.dto.FrameDto;
import ru.wink.winkaipreviz.dto.RegenerateFrameRequest;
import ru.wink.winkaipreviz.service.PrevizService;

import java.time.Instant;

@RestController
@RequestMapping("/api/frames")
public class FrameController {

    private final PrevizService service;

    public FrameController(PrevizService service) {
        this.service = service;
    }

    @PostMapping("/{frameId}/regenerate")
    public FrameDto regenerate(@PathVariable String frameId, @RequestBody RegenerateFrameRequest req) {
        return service.regenerateFrame(frameId, req);
    }
}


