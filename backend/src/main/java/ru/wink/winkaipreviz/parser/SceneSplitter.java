package ru.wink.winkaipreviz.parser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SceneSplitter — утилита для выделения сцен и формирования чанков из сценария.
 * Определяет сцены по структурным признакам:
 *   - необязательный номер (1., 1-2., 03А. и т.п.)
 *   - в строке встречается ИНТ./НАТ./INT./EXT.
 * Между номером и маркером допускается произвольный текст (например: ПРОЛОГ, СОН, ВОСПОМИНАНИЕ).
 * Защищено от ложных срабатываний на "НАТуральные", "ИНТЕРЕСНО" и подобные слова.
 */
public class SceneSplitter {

    // Основной шаблон начала сцены
    private static final Pattern SCENE_HEADER_PATTERN = Pattern.compile(
            "(?m)^\\s*" +
                    // возможный номер: 1., 1-2., 03А. и т.п.
                    "(?:\\d{1,3}(?:[-–—]\\d{1,3})?\\.?\\s*)?" +
                    // допускаем произвольный текст между номером и маркером (до 80 символов)
                    "(?:[\\p{L}\\p{N}\\s:()\\-–—]{0,80})?" +
                    // маркер начала сцены (ИНТ./НАТ./INT./EXT.), только если он отдельное слово
                    "(?<=\\s|^)(ИНТ\\.|НАТ\\.|ИНТ/НАТ\\.|ЭКСТ\\.|INT\\.|EXT\\.)(?=\\s)" +
                    // описание после маркера
                    "\\s+[^\\n]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /**
     * Делит текст сценария на отдельные сцены.
     * Каждая сцена — это блок от заголовка до следующего.
     */
    public static List<String> splitScenes(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        // нормализация пробелов и переносов строк (актуально для PDF)
        text = text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \t]+", " ")
                .replaceAll("\\n{3,}", "\n\n");

        Matcher matcher = SCENE_HEADER_PATTERN.matcher(text);
        List<Integer> sceneStartIndices = new ArrayList<>();

        while (matcher.find()) {
            sceneStartIndices.add(matcher.start());
        }

        if (sceneStartIndices.isEmpty()) {
            return List.of(text.trim());
        }

        sceneStartIndices.add(text.length()); // конец последней сцены
        List<String> scenes = new ArrayList<>();

        for (int i = 0; i < sceneStartIndices.size() - 1; i++) {
            int start = sceneStartIndices.get(i);
            int end = sceneStartIndices.get(i + 1);
            String block = text.substring(start, end).trim();
            if (!block.isEmpty()) scenes.add(block);
        }

        return scenes;
    }

    /**
     * Делит сцены на чанки по лимиту символов, не разрывая сцен.
     */
    public static List<List<String>> makeChunks(List<String> scenes, int charLimit) {
        List<List<String>> chunks = new ArrayList<>();
        if (scenes == null || scenes.isEmpty()) return chunks;

        List<String> currentChunk = new ArrayList<>();
        int currentSize = 0;

        for (String scene : scenes) {
            int len = scene.length();

            // если добавление сцены переполнит чанк — создаём новый
            if (currentSize + len > charLimit && !currentChunk.isEmpty()) {
                chunks.add(new ArrayList<>(currentChunk));
                currentChunk.clear();
                currentSize = 0;
            }

            currentChunk.add(scene);
            currentSize += len;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(new ArrayList<>(currentChunk));
            currentChunk.clear();
        }
        return chunks;
    }
}