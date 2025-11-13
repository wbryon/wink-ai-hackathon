package ru.wink.winkaipreviz.dto;

/**
 * Параметры апскейла кадра.
 */
public record UpscaleMetaDto(
        Double factor,
        Double denoise
) {}


