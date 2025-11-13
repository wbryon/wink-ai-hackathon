package ru.wink.winkaipreviz.entity;

/**
 * Уровень детализации кадра.
 * Соответствует LOD профилям, но используется для обратной совместимости.
 * Для новых функций рекомендуется использовать LODProfile напрямую.
 */
public enum DetailLevel {
    SKETCH, 
    MID, 
    FINAL,
    /**
     * Сразу финал без этапа эскиза (Direct Final).
     * Используется когда path=direct и detailLevel=final.
     */
    DIRECT_FINAL
}
