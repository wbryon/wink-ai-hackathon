package ru.wink.winkaipreviz.dto;

/**
 * Метаданные по каждому control-сигналу (ControlNet, IP-Adapter и т.п.).
 */
public record ControlMetaDto(
        String type,
        Double weight,
        Double start,
        Double end,
        String preprocessor
) {}


