package ru.wink.winkaipreviz.entity;

/**
 * Путь генерации кадра:
 * DIRECT      — прямой вызов генерации по сцене/промпту;
 * PROGRESSIVE — прогрессивная/итеративная генерация (вариант, продолжение, «кадр между»).
 */
public enum GenerationPath {
    DIRECT,
    PROGRESSIVE
}


