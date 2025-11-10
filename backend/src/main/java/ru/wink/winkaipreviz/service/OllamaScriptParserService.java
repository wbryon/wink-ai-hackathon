package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class OllamaScriptParserService {

    private final OllamaClient ollamaClient;

    public OllamaScriptParserService(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    /**
     * Разбивает сценарий на сцены и отправляет каждую сцену в Ollama для анализа.
     * Работает параллельно через виртуальные потоки.
     */
    public List<String> processScript(String fullScript) throws InterruptedException {
        List<String> scenes = splitScript(fullScript);
        if (scenes.isEmpty()) {
            return List.of("{\"error\": \"empty script\"}");
        }

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<Callable<String>> tasks = new ArrayList<>();
        for (String sceneText : scenes) {
            tasks.add(() -> {
                String prompt = """
                Разбей следующую сцену на смысловые элементы и верни ТОЛЬКО JSON, без объяснений, без тегов <think> и текста.
                Структура: {"location": "...", "time": "...", "characters": [...], "actions": [...]}.
                Ответ должен быть валидным JSON и не содержать других символов.
                Сцена:
                %s
                """.formatted(sceneText);

                // 🟢 Генерируем ответ от модели
                String raw = ollamaClient.generateText(prompt).block();

                // 🧹 Очищаем его перед возвратом
                return cleanModelOutput(raw);
            });
        }

        List<Future<String>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        List<String> results = new ArrayList<>();
        for (Future<String> f : futures) {
            try {
                results.add(f.get());
            } catch (ExecutionException e) {
                results.add("{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
        return results;
    }

    /**
     * Вызывает Ollama API для анализа одной сцены.
     */
    private String processScene(String sceneText) {
        String prompt = """
Разбей следующую сцену на смысловые элементы и верни ТОЛЬКО JSON.
Структура JSON:
{
  "location": "место действия",
  "time": "время суток",
  "characters": ["список всех субъектов (кто или что выполняет действие)"],
  "actions": ["список действий (что происходит, глаголы или фразы действия)"]
}
Ответ должен быть строго в формате JSON, без текста и тегов.
Сцена:
%s
""".formatted(sceneText);

        // блокируем реактивный Mono, чтобы совместить с ExecutorService
        String raw = ollamaClient.generateText(prompt).block();
        return cleanModelOutput(raw);
    }

    /**
     * Делит сценарий на сцены по шаблонам INT./EXT.
     */
    private List<String> splitScript(String fullScript) {
        if (fullScript == null || fullScript.isBlank()) return List.of();
        // делим сценарий по заголовкам сцен
        return List.of(fullScript.split("(?=INT\\.|EXT\\.)"));
    }

    private String cleanModelOutput(String raw) {
        if (raw == null) return "";
        // Удаляем блок <think> ... </think> и любые пробелы до/после
        return raw.replaceAll("(?s)<think>.*?</think>\\s*", "").trim();
    }

}

/**
 String prompt = """
 Разбей следующую сцену из сценария на смысловые элементы и верни ТОЛЬКО JSON.
 Не добавляй объяснений, размышлений или тегов <think>.

 🎬 Структура JSON должна быть строго такой:
 {
 "location": "место действия (одно слово или короткая фраза)",
 "time": "время суток (например, DAY, NIGHT, EVENING и т.д.)",
 "characters": ["список всех участников сцены — людей, существ, машин и т.п."],
 "actions": ["список действий, происходящих в сцене (глаголы или короткие описания событий)"]
 }

 ❗️Правила:
 - Если сцена начинается с INT. или EXT. — это определяет location и time.
 - Каждый персонаж или субъект действия должен быть в массиве "characters".
 - Каждое действие (verb phrase) — в массиве "actions".
 - Если персонажей нет, оставь "characters": [].
 - Ответ должен быть корректным JSON и не содержать других символов.

 Пример:
 Сцена:
 INT. OFFICE - DAY. John types on his laptop while Mary drinks coffee.

 Ответ:
 {
 "location": "OFFICE",
 "time": "DAY",
 "characters": ["John", "Mary"],
 "actions": ["John types on his laptop", "Mary drinks coffee"]
 }

 Теперь проанализируй сцену:
 %s
 """.formatted(sceneText);
 */