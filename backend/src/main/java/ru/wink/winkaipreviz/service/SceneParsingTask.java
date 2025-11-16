package ru.wink.winkaipreviz.service;

import java.util.UUID;

/**
 * Lightweight task descriptor for background scene parsing.
 * The worker will load the latest Scene entity by ID and run the Ollama-based parser.
 */
public class SceneParsingTask {

    private final UUID scriptId;
    private final UUID sceneId;

    public SceneParsingTask(UUID scriptId, UUID sceneId) {
        this.scriptId = scriptId;
        this.sceneId = sceneId;
    }

    public UUID getScriptId() {
        return scriptId;
    }

    public UUID getSceneId() {
        return sceneId;
    }
}


