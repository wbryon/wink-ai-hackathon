package ru.wink.winkaipreviz.entity;

public enum VisualStatus {
    PROMPT_READY,   // есть prompt, но ещё нет картинки
    IMAGE_READY,    // картинка сгенерирована, imageUrl заполнен
    FAILED          // что-то пошло не так на любом шаге пайплайна
}

