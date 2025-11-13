package ru.wink.winkaipreviz.dto;

/**
 * Настройки refiner-этапа (второй проход модели).
 */
public record RefinerMetaDto(
        Boolean on,
        Double denoise
) {}


