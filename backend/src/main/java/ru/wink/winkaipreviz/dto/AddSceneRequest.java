package ru.wink.winkaipreviz.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO для ручного добавления сцены пользователем.
 */
public class AddSceneRequest {

    private String title;
    private String location;
    private String description;
    private List<String> characters = new ArrayList<>();
    private List<String> props = new ArrayList<>();
    private String tone;
    private String style;
    private String semanticSummary;

    // --- getters/setters ---
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getCharacters() { return characters; }
    public void setCharacters(List<String> characters) { this.characters = characters; }

    public List<String> getProps() { return props; }
    public void setProps(List<String> props) { this.props = props; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public String getSemanticSummary() { return semanticSummary; }
    public void setSemanticSummary(String semanticSummary) { this.semanticSummary = semanticSummary; }
}
