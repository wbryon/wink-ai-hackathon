package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wink.winkaipreviz.ai.ImageResult;
import ru.wink.winkaipreviz.entity.*;
import ru.wink.winkaipreviz.repository.*;
import ru.wink.winkaipreviz.ai.ParserPort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Асинхронная обработка сценария:
 * 1. Извлечение текста
 * 2. Парсинг сцен
 * 3. Генерация эскизов/финальных кадров
 */
@Service
public class SceneGenerationService {

    private final ScriptRepository scriptRepository;
    private final SceneRepository sceneRepository;
    private final FrameRepository frameRepository;
    private final FileStorageService fileStorageService;
    private final ScriptProcessorClient scriptProcessorClient;
    private final ParserPort parserClient;
    private final AiImageClient imageClient;

    public SceneGenerationService(ScriptRepository scriptRepository,
                                  SceneRepository sceneRepository,
                                  FrameRepository frameRepository,
                                  FileStorageService fileStorageService,
                                  ScriptProcessorClient scriptProcessorClient,
                                  ParserPort parserClient,
                                  AiImageClient imageClient) {
        this.scriptRepository = scriptRepository;
        this.sceneRepository = sceneRepository;
        this.frameRepository = frameRepository;
        this.fileStorageService = fileStorageService;
        this.scriptProcessorClient = scriptProcessorClient;
        this.parserClient = parserClient;
        this.imageClient = imageClient;
    }

    /**
     * Асинхронный процесс — отдельный поток (virtual thread)
     */
    public void processScriptAsync(UUID scriptId) {
        Thread.ofVirtual().start(() -> {
            Script script = scriptRepository.findById(scriptId)
                    .orElseThrow(() -> new IllegalArgumentException("Script not found"));

            try {
                script.setStatus(ScriptStatus.PARSING);
                scriptRepository.save(script);

                // 1️⃣ Получаем сцены напрямую через script-processor
                java.nio.file.Path path = java.nio.file.Path.of(script.getFilePath());
                List<ScriptProcessorClient.SceneData> sceneDataList = scriptProcessorClient.splitToScenes(path);
                
                // 2️⃣ Создаём Scene объекты из данных script-processor
                List<Scene> parsedScenes = new java.util.ArrayList<>();
                StringBuilder allText = new StringBuilder();
                
                for (ScriptProcessorClient.SceneData sceneData : sceneDataList) {
                    Scene scene = new Scene();
                    scene.setScript(script);
                    scene.setTitle(sceneData.slugline());
                    scene.setLocation(sceneData.place());
                    scene.setDescription(sceneData.text());
                    scene.setStatus(SceneStatus.PARSED);
                    parsedScenes.add(scene);
                    
                    if (allText.length() > 0) {
                        allText.append("\n\n");
                    }
                    allText.append(sceneData.text());
                }
                
                sceneRepository.saveAll(parsedScenes);
                
                // Сохраняем извлечённый текст
                if (allText.length() > 0) {
                    script.setTextExtracted(allText.toString());
                }
                
                script.setStatus(parsedScenes.isEmpty() ? ScriptStatus.FAILED : ScriptStatus.PARSED);
                scriptRepository.save(script);

                // 3️Генерация изображений по запросу пользователя
                // Оставляем сценарий в статусе PARSED
                scriptRepository.save(script);

            } catch (Exception e) {
                script.setStatus(ScriptStatus.FAILED);
                scriptRepository.save(script);
                e.printStackTrace();
            }
        });
    }

    @Transactional
    public void generateSceneFrame(Scene scene, DetailLevel level) {
        Instant start = Instant.now();
        scene.setStatus(SceneStatus.GENERATING);
        sceneRepository.save(scene);

        // Простой промпт по данным сцены (SceneGenerationService используется редко, поэтому без сложного LLM-пайплайна)
        StringBuilder sb = new StringBuilder();
        sb.append("Scene ").append(scene.getTitle() == null ? "" : scene.getTitle())
                .append(" / ").append(scene.getLocation() == null ? "" : scene.getLocation())
                .append(". ");
        if (scene.getDescription() != null) {
            sb.append(scene.getDescription());
        }
        String prompt = sb.toString();
        ImageResult result = imageClient.generate(prompt, level);

        Frame frame = new Frame();
        frame.setScene(scene);
        frame.setPrompt(prompt);
        frame.setDetailLevel(level);
        frame.setImageUrl(result.imageUrl());
        frame.setModel(result.model());
        frame.setSeed(result.seed());
        if (result.metaJson() != null && !result.metaJson().isBlank()) {
            frame.setEnrichedJson(result.metaJson());
        }
        frame.setGenerationMs(java.time.Duration.between(start, Instant.now()).toMillis());
        frameRepository.save(frame);

        scene.setStatus(SceneStatus.READY);
        sceneRepository.save(scene);
    }
}
