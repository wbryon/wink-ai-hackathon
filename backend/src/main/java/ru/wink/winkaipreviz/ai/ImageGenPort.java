package ru.wink.winkaipreviz.ai;

import ru.wink.winkaipreviz.entity.DetailLevel;

public interface ImageGenPort {
    ImageResult generate(String prompt, DetailLevel level);
}
