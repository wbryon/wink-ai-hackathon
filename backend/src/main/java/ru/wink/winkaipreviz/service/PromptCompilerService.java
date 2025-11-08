package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.entity.DetailLevel;

import java.util.Optional;
import java.util.StringJoiner;

/**
 * Формирует промпт для модели (SDXL / Flux) из данных сцены.
 * Работает с русским сценарием — тон, стиль, персонажи, локация, действие.
 */
@Service
public class PromptCompilerService {

    public String compile(Scene scene, DetailLevel level) {
        String title = Optional.ofNullable(scene.getTitle()).orElse("Без названия");
        String location = Optional.ofNullable(scene.getLocation()).orElse("Неизвестное место");
        String tone = Optional.ofNullable(scene.getTone()).orElse("нейтральный");
        String style = Optional.ofNullable(scene.getStyle()).orElse("кинематографичный");
        String description = Optional.ofNullable(scene.getSemanticSummary()).orElse(scene.getDescription());
        String characters = join(scene.getCharacters());
        String props = join(scene.getProps());

        return """
                # КОНТЕКСТ
                Сцена: %s
                Локация: %s
                Настроение: %s
                Стиль: %s

                # ПЕРСОНАЖИ
                %s

                # РЕКВИЗИТ
                %s

                # ДЕЙСТВИЕ
                %s

                # ДЕТАЛИЗАЦИЯ
                Уровень: %s
                """.formatted(
                title,
                location,
                tone,
                style,
                characters.isEmpty() ? "—" : characters,
                props.isEmpty() ? "—" : props,
                description == null ? "Нет описания." : description,
                level.name()
        );
    }

    private String join(Iterable<String> items) {
        if (items == null) return "";
        StringJoiner joiner = new StringJoiner(", ");
        for (String i : items) if (i != null && !i.isBlank()) joiner.add(i.trim());
        return joiner.toString();
    }
}
