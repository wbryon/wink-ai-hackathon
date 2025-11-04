package ru.wink.winkaipreviz.parser;

import org.springframework.stereotype.Component;
import ru.wink.winkaipreviz.entity.Scene;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Простой MVP-парсер сценариев.
 * Делит текст на сцены по паттернам ИНТ./НАТ./INT./EXT.,
 * извлекает название, локацию, персонажей и описание.
 */
@Component
public class RuleParser {

    // Простой рабочий шаблон (fallback)
    private static final Pattern SIMPLE_SCENE_HEADER_PATTERN = Pattern.compile(
            "(?m)^(\\d+\\s*[.–—-]?\\s*)?(ИНТ\\.|НАТ\\.|INT\\.|EXT\\.)\\s+([^\\n]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // Основной шаблон для начала сцены (компилируем безопасно в конструкторе)
    private final Pattern sceneHeaderPattern;

    public RuleParser() {
        Pattern compiled;
        try {
            compiled = Pattern.compile(
                    "(?m)^(?:" +
                            // optional complex scene index like "1.1", "1-3-N1." etc
                            "(?:[\\p{L}\\p{N}\\.\\-]+\\.?\\s+)?" +
                            // INT/EXT in RU/EN with optional dot
                            "((?:ИНТЕРЬЕР|ИНТ|НАТУРАЛЬН(?:АЯ|О)|НАТ|INT|EXT)\\.?)\\s+([^\\n]+)" +
                        "|" +
                            // SCENE / СЦЕНА header
                            "(?:(SCENE|СЦЕНА)\\s+\\d+[:\\-\\.]?\\s*([^\\n]+))" +
                    ")",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );
        } catch (PatternSyntaxException ex) {
            compiled = SIMPLE_SCENE_HEADER_PATTERN; // безопасный откат
        }
        this.sceneHeaderPattern = compiled;
    }

    // Заглавные слова (часто имена персонажей)
    private static final Pattern CHARACTER_PATTERN = Pattern.compile("(?m)^[A-ZА-ЯЁ][A-ZА-ЯЁ\\s\\-]{2,}$");
           private static final Pattern LOWERCASE_LETTERS = Pattern.compile("[a-zа-яё]");

    /**
     * Главный метод: парсит сценарий и возвращает список сцен.
     */
    public List<Scene> parse(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        List<Scene> scenes = new ArrayList<>();
        Matcher matcher = sceneHeaderPattern.matcher(text);

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
        Matcher headerMatcher = sceneHeaderPattern.matcher(block);
        if (!headerMatcher.find()) return null;

        String header = headerMatcher.group(0).trim();
        String title = header;
        String location = extractLocation(headerMatcher);
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
    private String extractLocation(Matcher headerMatcher) {
        // If matched by INT/EXT variant, header groups: 1) kind 2) rest
        String kind = safeGroup(headerMatcher, 1);
        String rest = safeGroup(headerMatcher, 2);
        // If matched by SCENE/СЦЕНА, header groups: 3) word 4) rest
        if (kind == null || kind.isBlank()) {
            rest = rest == null ? safeGroup(headerMatcher, 4) : rest;
        }
        String loc = rest == null ? headerMatcher.group(0) : rest;
        return loc.replaceAll("\\s*(ДЕНЬ|НОЧЬ|ДЕНЬ\\.|НОЧЬ\\.|DAY|NIGHT)\\s*", "").trim();
    }

    private static String safeGroup(Matcher m, int idx) {
        try {
            return m.group(idx);
        } catch (Exception e) {
            return null;
        }
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
              // Preserve line breaks, but collapse multiple consecutive newlines to a single one
              if (text == null) return "";
              String withUnixNewlines = text.replace("\r\n", "\n").replace("\r", "\n");
              // Replace 2+ consecutive newlines with a single newline
              String collapsed = withUnixNewlines.replaceAll("\n{2,}", "\n");
              return collapsed.trim();
          }

           private boolean isUpperLikeLine(String line) {
               if (line.isEmpty()) return false;
               // If contains any lowercase latin/cyrillic letter, it's not an uppercase name/list line
               if (LOWERCASE_LETTERS.matcher(line).find()) return false;
               // Also accept classic pure-character line pattern
               if (CHARACTER_PATTERN.matcher(line).matches()) return true;
               // Allow commas, digits, parentheses in all-caps lists
               return line.matches("^[A-ZА-ЯЁ0-9\\s,.'()\"-]{2,}$")
                       && line.replaceAll("[^A-ZА-ЯЁ]", "").length() >= 2;
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

