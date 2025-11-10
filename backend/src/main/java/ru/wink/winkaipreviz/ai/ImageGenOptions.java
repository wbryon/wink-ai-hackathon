package ru.wink.winkaipreviz.ai;

public record ImageGenOptions(
        String model,
        Integer steps,
        Double denoise,
        String detailLevel,
        String refImageUrl
) {}
