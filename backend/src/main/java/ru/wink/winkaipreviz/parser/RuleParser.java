package ru.wink.winkaipreviz.parser;

import org.springframework.stereotype.Component;
import ru.wink.winkaipreviz.entity.Scene;

import java.util.*;
import java.util.regex.*;

/**
 * Простой MVP-парсер сценариев.
 * Делит текст на сцены по паттернам ИНТ./НАТ./INT./EXT.,
 * извлекает название, локацию, персонажей и описание.
 */
@Component
public class RuleParser {

    // Основной шаблон для начала сцены
    private static final Pattern SCENE_HEADER_PATTERN = Pattern.compile(
            "(?m)^(\\d+\\s*[.–—-]?\\s*)?(ИНТ\\.|НАТ\\.|INT\\.|EXT\\.)\\s+([^\\n]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // Заглавные слова (часто имена персонажей)
    private static final Pattern CHARACTER_PATTERN = Pattern.compile("(?m)^[A-ZА-ЯЁ][A-ZА-ЯЁ\\s\\-]{2,}$");

    /**
     * Главный метод: парсит сценарий и возвращает список сцен.
     */
    public List<Scene> parse(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        List<Scene> scenes = new ArrayList<>();
        Matcher matcher = SCENE_HEADER_PATTERN.matcher(text);

        List<Integer> sceneStartIndices = new ArrayList<>();
        while (matcher.find()) {
            sceneStartIndices.add(matcher.start());
        }
        sceneStartIndices.add(text.length()); // конец последней сцены

        for (int i = 0; i < sceneStartIndices.size() - 1; i++) {
            int start = sceneStartIndices.get(i);
            int end = sceneStartIndices.get(i + 1);
            String block = text.substring(start, end).trim();

            Scene scene = parseSceneBlock(block, i + 1);
            if (scene != null) {
                scenes.add(scene);
            }
        }

        return scenes;
    }

    /**
     * Парсинг отдельного блока текста (одной сцены)
     */
    private Scene parseSceneBlock(String block, int index) {
        Matcher headerMatcher = SCENE_HEADER_PATTERN.matcher(block);
        if (!headerMatcher.find()) return null;

        String header = headerMatcher.group(0).trim();
        String title = header;
        String location = extractLocation(header);
        String body = block.substring(headerMatcher.end()).trim();

        List<String> characters = extractCharacters(body);
        String description = extractDescription(body);

        Scene scene = new Scene();
        scene.setTitle(title);
        scene.setLocation(location);
        scene.setCharacters(characters);
        scene.setDescription(description);
        scene.setProps(new ArrayList<>());  // пустой список для совместимости

        return scene;
    }


    /**
     * Извлекает локацию из заголовка (всё после INT./EXT.)
     */
    private String extractLocation(String header) {
        String[] parts = header.split("\\s+", 3);
        if (parts.length >= 3) {
            return parts[2].replaceAll("\\s*(ДЕНЬ|НОЧЬ|ДЕНЬ\\.|НОЧЬ\\.|DAY|NIGHT)\\s*", "").trim();
        }
        return header;
    }

    /**
     * Извлекает имена персонажей по заглавным строкам
     */
    private List<String> extractCharacters(String text) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = CHARACTER_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group().trim();
            // отсекаем длинные строки и технические слова
            if (raw.length() <= 40 && !raw.matches("ИНТ\\.|НАТ\\.|EXT\\.|INT\\.")) {
                names.add(raw);
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Описание сцены: всё до первой реплики
     */
    private String extractDescription(String text) {
        String[] lines = text.split("\n");
        StringBuilder desc = new StringBuilder();
        for (String line : lines) {
            if (CHARACTER_PATTERN.matcher(line.trim()).matches()) break;
            desc.append(line.trim()).append(" ");
        }
        return desc.toString().replaceAll("\\s{2,}", " ").trim();
    }

    // Для теста
    public static void main(String[] args) {
        String example = """
                1. ИНТ. КВАРТИРА МАШИ - УТРО
                Маша завтракает у окна. За окном шумит дождь.
                ИГОРЬ
                Доброе утро, Маша.

                2. НАТ. ДВОР ШКОЛЫ - ДЕНЬ
                Дети играют в мяч. Маша выходит из подъезда.
                """;

        RuleParser parser = new RuleParser();
        List<Scene> scenes = parser.parse(example);
        scenes.forEach(System.out::println);
    }
}

