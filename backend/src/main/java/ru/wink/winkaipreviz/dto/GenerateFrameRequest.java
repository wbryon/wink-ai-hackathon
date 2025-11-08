package ru.wink.winkaipreviz.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class GenerateFrameRequest {

    @Pattern(regexp = "(?i)sketch|mid|final", message = "detailLevel должен быть одним из: sketch, mid, final")
    private String detailLevel = "SKETCH";

    @Size(max = 4000, message = "prompt слишком длинный (макс. 4000 символов)")
    private String prompt;

    private Integer seed;

    @Size(max = 64, message = "model слишком длинное (макс. 64 символа)")
    private String model;

    // --- getters/setters ---
    public String getDetailLevel() { return detailLevel; }
    public void setDetailLevel(String detailLevel) { this.detailLevel = detailLevel; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
