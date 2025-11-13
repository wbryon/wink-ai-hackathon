package ru.wink.winkaipreviz.dto;

import java.util.List;

/**
 * Полная карточка кадра (frame-card) для экспорта и аналитики.
 * Объединяет данные сцены, кадра, слотов и технических метаданных.
 */
public record FrameCardDto(
        String frameId,
        String sceneId,
        String scriptId,

        String sceneTitle,
        String sceneLocation,
        List<String> sceneCharacters,
        List<String> sceneProps,

        String lod,
        String path,
        String prompt,
        String imageUrl,
        String createdAt,
        Boolean best,

        PromptSlotsDto slots,
        FrameTechMetaDto meta
) {}


