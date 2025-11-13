package ru.wink.winkaipreviz.ai;

import java.time.Instant;

/**
 * Результат генерации изображения от AI-сервиса.
 * Помимо базовых полей может содержать raw-метаданные генерации в виде JSON-строки,
 * которые далее маппятся в FrameTechMetaDto и сохраняются в Frame.enrichedJson.
 */
public record ImageResult(
        String imageUrl,
        String model,
        Integer seed,
        Instant createdAt,
        String metaJson
) {}
