package ru.wink.winkaipreviz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class ScenesWebhookRequest {

    @NotBlank
    private String scriptId;

    @NotNull
    @Valid
    private List<IncomingSceneDto> scenes = new ArrayList<>();

    // --- getters/setters ---
    public String getScriptId() { return scriptId; }
    public void setScriptId(String scriptId) { this.scriptId = scriptId; }

    public List<IncomingSceneDto> getScenes() { return scenes; }
    public void setScenes(List<IncomingSceneDto> scenes) { this.scenes = scenes; }
}


