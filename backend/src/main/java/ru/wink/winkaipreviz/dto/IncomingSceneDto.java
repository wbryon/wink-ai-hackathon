package ru.wink.winkaipreviz.dto;

import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class IncomingSceneDto {

    // Внешний идентификатор от text-ai (идемпотентность по нему, если есть)
    @Size(max = 128)
    private String externalId;

    @Size(max = 512)
    private String title;

    @Size(max = 256)
    private String location;

    // Оригинальное описание сцены (как есть из парсера)
    private String description;

    // Семантический пересказ (если есть у парсера)
    private String semanticSummary;

    @Size(max = 128)
    private String tone;

    @Size(max = 128)
    private String style;

    private List<String> characters = new ArrayList<>();
    private List<String> props = new ArrayList<>();

    // --- getters/setters ---
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

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

    public List<String> getCharacters() { return characters; }
    public void setCharacters(List<String> characters) { this.characters = characters; }

    public List<String> getProps() { return props; }
    public void setProps(List<String> props) { this.props = props; }
}


