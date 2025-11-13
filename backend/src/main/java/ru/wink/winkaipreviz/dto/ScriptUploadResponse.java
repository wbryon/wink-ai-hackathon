package ru.wink.winkaipreviz.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Ответ после загрузки сценария.
 */
public class ScriptUploadResponse {

    private String scriptId;
    private String filename;
    private String status;
    private List<SceneDto> scenes = new ArrayList<>();
    private List<String> chunkFiles = new ArrayList<>();

    // --- getters/setters ---
    public String getScriptId() { return scriptId; }
    public void setScriptId(String scriptId) { this.scriptId = scriptId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<SceneDto> getScenes() { return scenes; }
    public void setScenes(List<SceneDto> scenes) { this.scenes = scenes; }

    public List<String> getChunkFiles() { return chunkFiles; }
    public void setChunkFiles(List<String> chunkFiles) { this.chunkFiles = chunkFiles; }
}
