package ru.wink.winkaipreviz.dto;

public class GenerateFrameRequest {

    private String detailLevel = "SKETCH";
    private String prompt;
    private Integer seed;
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
