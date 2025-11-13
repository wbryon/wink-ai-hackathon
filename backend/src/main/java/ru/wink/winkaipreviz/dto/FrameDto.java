package ru.wink.winkaipreviz.dto;

/**
 * DTO для отображения сгенерированного кадра.
 * Включает базовые поля и технические метаданные (frame meta).
 */
public class FrameDto {

    private String id;
    private String sceneId;
    private String imageUrl;
    private String detailLevel;
    private String path;
    private String prompt;
    private Integer seed;
    private String model;
    private String createdAt;
    private FrameTechMetaDto meta;

    // --- getters/setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSceneId() { return sceneId; }
    public void setSceneId(String sceneId) { this.sceneId = sceneId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getDetailLevel() { return detailLevel; }
    public void setDetailLevel(String detailLevel) { this.detailLevel = detailLevel; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public FrameTechMetaDto getMeta() { return meta; }
    public void setMeta(FrameTechMetaDto meta) { this.meta = meta; }
}
