package ru.wink.winkaipreviz.entity;

public enum SceneStatus {
    /**
     * Initial state right after scene is created from raw script text,
     * waiting to be parsed by the Ollama-based scene parser.
     */
    PENDING,

    /**
     * Scene is currently being processed by a background worker.
     */
    PROCESSING,

    /**
     * Scene text has been successfully parsed and enriched into structured fields.
     */
    PARSED,

    /**
     * Image generation is in progress for this scene.
     */
    GENERATING,

    /**
     * At least one frame has been successfully generated for this scene.
     */
    READY,

    /**
     * Parsing or generation failed with an unrecoverable error.
     */
    FAILED
}
