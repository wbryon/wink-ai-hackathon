package ru.wink.winkaipreviz.entity;

import jakarta.persistence.*;
import java.time.Duration;
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

    @Column(nullable = false)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DetailLevel detailLevel = DetailLevel.SKETCH;

    @Lob
    @Column(columnDefinition = "text")
    private String prompt;

    private Integer seed;

    @Column(length = 64)
    private String model;

    private Boolean isBest = false;

    private Duration generationTime;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // --- getters/setters ---
    public UUID getId() { return id; }
    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public DetailLevel getDetailLevel() { return detailLevel; }
    public void setDetailLevel(DetailLevel detailLevel) { this.detailLevel = detailLevel; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Boolean getIsBest() { return isBest; }
    public void setIsBest(Boolean isBest) { this.isBest = isBest; }
    public Duration getGenerationTime() { return generationTime; }
    public void setGenerationTime(Duration generationTime) { this.generationTime = generationTime; }
    public Instant getCreatedAt() { return createdAt; }
}
