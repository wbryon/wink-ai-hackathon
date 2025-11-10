package ru.wink.winkaipreviz.ai;

import ru.wink.winkaipreviz.entity.Scene;

import java.util.List;

public interface ParserPort {
    List<Scene> parseScenes(String text);
    String getLastRawJson();
}
