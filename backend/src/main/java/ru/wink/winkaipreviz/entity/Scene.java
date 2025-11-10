package ru.wink.winkaipreviz.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scene — отдельная сцена сценария.
 * Хранит локацию, персонажей, описание, стиль и тон.
 */
@Entity
@Table(name = "scenes")
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    /** Внешний идентификатор сцены от text-ai (для идемпотентности) */
    @Column(name = "external_id", length = 128)
    private String externalId;

    /** Хэш-отпечаток содержимого сцены (для идемпотентности при отсутствии externalId) */
    @Column(name = "dedup_hash", length = 64)
    private String dedupHash;

    /** Заголовок сцены, например "ИНТ. КАФЕ У ОКНА — ВЕЧЕР" */
    @Column(length = 512)
    private String title;

    @Column(length = 256)
    private String location;

    /** Персонажи в сцене */
    @ElementCollection
    @CollectionTable(name = "scene_characters", joinColumns = @JoinColumn(name = "scene_id"))
    @Column(name = "character")
    private List<String> characters = new ArrayList<>();

    /** Реквизит */
    @ElementCollection
    @CollectionTable(name = "scene_props", joinColumns = @JoinColumn(name = "scene_id"))
    @Column(name = "prop")
    private List<String> props = new ArrayList<>();

    /** Описание действий в сцене */
    @Lob
    @Column(columnDefinition = "text")
    private String description;

    /** Семантический пересказ — что происходит (заполняется LLM) */
    @Lob
    @Column(columnDefinition = "text")
    private String semanticSummary;

    /** Эмоциональный тон сцены */
    @Column(length = 128)
    private String tone;

    /** Визуальный стиль (например "нуар", "драматичный", "теплый") */
    @Column(length = 128)
    private String style;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SceneStatus status = SceneStatus.PARSED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<Frame> frames = new ArrayList<>();

    // --- getters/setters ---
    public UUID getId() { return id; }
    public Script getScript() { return script; }
    public void setScript(Script script) { this.script = script; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getDedupHash() { return dedupHash; }
    public void setDedupHash(String dedupHash) { this.dedupHash = dedupHash; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public List<String> getCharacters() { return characters; }
    public void setCharacters(List<String> characters) { this.characters = characters; }
    public List<String> getProps() { return props; }
    public void setProps(List<String> props) { this.props = props; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSemanticSummary() { return semanticSummary; }
    public void setSemanticSummary(String semanticSummary) { this.semanticSummary = semanticSummary; }
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public SceneStatus getStatus() { return status; }
    public void setStatus(SceneStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Frame> getFrames() { return frames; }
}
