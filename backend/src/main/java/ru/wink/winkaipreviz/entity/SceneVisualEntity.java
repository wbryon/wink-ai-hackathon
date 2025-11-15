package ru.wink.winkaipreviz.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scene_visual")
public class SceneVisualEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Ссылка на сцену (Scene.id)
     */
    @Column(name = "scene_id", nullable = false)
    private UUID sceneId;

    /**
     * Enriched JSON после Missing-Data Enhancer
     * Храним как jsonb, но в Java — обычная String.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enriched_json", columnDefinition = "jsonb", nullable = false)
    private String enrichedJson;

    /**
     * Финальный текстовый промпт для Flux Schnell
     */
    @Lob
    @Column(name = "flux_prompt", columnDefinition = "text", nullable = false)
    private String fluxPrompt;

    /**
     * URL / path до сгенерированного кадра (может быть null, если ещё не генерили картинку)
     */
    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VisualStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = VisualStatus.PROMPT_READY;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // геттеры/сеттеры

    public Long getId() { return id; }

    public UUID getSceneId() { return sceneId; }
    public void setSceneId(UUID sceneId) { this.sceneId = sceneId; }

    public String getEnrichedJson() { return enrichedJson; }
    public void setEnrichedJson(String enrichedJson) { this.enrichedJson = enrichedJson; }

    public String getFluxPrompt() { return fluxPrompt; }
    public void setFluxPrompt(String fluxPrompt) { this.fluxPrompt = fluxPrompt; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public VisualStatus getStatus() { return status; }
    public void setStatus(VisualStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}