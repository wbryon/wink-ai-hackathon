package ru.wink.winkaipreviz.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Script — сущность сценария, загруженного пользователем.
 * Хранит метаданные файла, текст сценария и статус обработки.
 */
@Entity
@Table(name = "scripts")
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScriptStatus status = ScriptStatus.UPLOADED;

    /** Текст, извлечённый из PDF/DOCX */
    @Lob
    @Column(columnDefinition = "text")
    private String textExtracted;

    /** JSON-ответ от AI-парсера (для анализа и отладки) */
    @Lob
    @Column(columnDefinition = "text")
    private String parsedJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "script", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Scene> scenes = new ArrayList<>();

    // --- getters/setters ---
    public UUID getId() { return id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public ScriptStatus getStatus() { return status; }
    public void setStatus(ScriptStatus status) { this.status = status; }
    public String getTextExtracted() { return textExtracted; }
    public void setTextExtracted(String textExtracted) { this.textExtracted = textExtracted; }
    public String getParsedJson() { return parsedJson; }
    public void setParsedJson(String parsedJson) { this.parsedJson = parsedJson; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Scene> getScenes() { return scenes; }
}
