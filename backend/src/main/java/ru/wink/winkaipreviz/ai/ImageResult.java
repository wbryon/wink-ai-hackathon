package ru.wink.winkaipreviz.ai;

import java.time.Instant;

public record ImageResult(
        String imageUrl,
        String model,
        Integer seed,
        Instant createdAt
) {}
