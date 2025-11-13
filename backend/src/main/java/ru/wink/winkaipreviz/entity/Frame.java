package ru.wink.winkaipreviz.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Frame — отдельный кадр, сгенерированный по сцене.
 */
@Entity
@Table(name = "frames")
public class Frame {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    /**
     * Родительский кадр для progressive path.
     * Например, для Mid родителем является Sketch, для Final — Mid.
     * Null для кадров, сгенерированных напрямую (DIRECT path).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_frame_id")
    private Frame parentFrame;

    @Column(nullable = false)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DetailLevel detailLevel = DetailLevel.SKETCH;

    /**
     * Путь генерации кадра (DIRECT/PROGRESSIVE).
     * Хранится отдельно, чтобы отличать прямую генерацию от прогрессивных вариантов.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_path", length = 16)
    private GenerationPath generationPath = GenerationPath.DIRECT;

    @Lob
    @Column(columnDefinition = "text")
    private String prompt;

    /**
     * Технические метаданные кадра (frame-card / meta),
     * сериализованные в JSON (см. FrameTechMetaDto на DTO-слое).
     */
    @Lob
    @Column(name = "enriched_json", columnDefinition = "text")
    private String enrichedJson;

    private Integer seed;

    @Column(length = 64)
    private String model;

    private Boolean isBest = false;

    @Column(name = "generation_ms")
    private Long generationMs;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // --- getters/setters ---
    public UUID getId() { return id; }
    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }
    public Frame getParentFrame() { return parentFrame; }
    public void setParentFrame(Frame parentFrame) { this.parentFrame = parentFrame; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public DetailLevel getDetailLevel() { return detailLevel; }
    public void setDetailLevel(DetailLevel detailLevel) { this.detailLevel = detailLevel; }
    public GenerationPath getGenerationPath() { return generationPath; }
    public void setGenerationPath(GenerationPath generationPath) { this.generationPath = generationPath; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getEnrichedJson() { return enrichedJson; }
    public void setEnrichedJson(String enrichedJson) { this.enrichedJson = enrichedJson; }
    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Boolean getIsBest() { return isBest; }
    public void setIsBest(Boolean isBest) { this.isBest = isBest; }
    public Long getGenerationMs() { return generationMs; }
    public void setGenerationMs(Long generationMs) { this.generationMs = generationMs; }
    public Instant getCreatedAt() { return createdAt; }
}
