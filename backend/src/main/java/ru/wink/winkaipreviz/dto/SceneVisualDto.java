package ru.wink.winkaipreviz.dto;

import java.util.UUID;

public record SceneVisualDto(
        Long id,
        UUID sceneId,
        String enrichedJson,
        String fluxPrompt,
        String imageUrl,
        String status,
        PromptSlotsDto slots
) {}

