package ru.wink.winkaipreviz.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO для передачи информации о сцене и связанных кадрах.
 */
public class SceneDto {

    private String id;
    private String title;
    private String location;
    private String description;
    private String semanticSummary;
    private String tone;
    private String style;
    private String status;

    private List<String> characters = new ArrayList<>();
    private List<String> props = new ArrayList<>();

    /** Raw JSON returned by the scene parser (Ollama) - base JSON после парсинга сцены. */
    private String originalJson;

    private List<FrameDto> generatedFrames = new ArrayList<>();
    private FrameDto currentFrame;

    // --- getters/setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSemanticSummary() { return semanticSummary; }
    public void setSemanticSummary(String semanticSummary) { this.semanticSummary = semanticSummary; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getCharacters() { return characters; }
    public void setCharacters(List<String> characters) { this.characters = characters; }

    public List<String> getProps() { return props; }
    public void setProps(List<String> props) { this.props = props; }

    public String getOriginalJson() { return originalJson; }
    public void setOriginalJson(String originalJson) { this.originalJson = originalJson; }

    public List<FrameDto> getGeneratedFrames() { return generatedFrames; }
    public void setGeneratedFrames(List<FrameDto> generatedFrames) { this.generatedFrames = generatedFrames; }

    public FrameDto getCurrentFrame() { return currentFrame; }
    public void setCurrentFrame(FrameDto currentFrame) { this.currentFrame = currentFrame; }
}
