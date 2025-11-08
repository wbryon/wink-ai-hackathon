package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wink.winkaipreviz.ai.ImageResult;
import ru.wink.winkaipreviz.entity.*;
import ru.wink.winkaipreviz.repository.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Асинхронная обработка сценария:
 * 1. Извлечение текста
 * 2. Парсинг сцен (AI или RuleParser)
 * 3. Генерация эскизов/финальных кадров
 */
@Service
public class SceneGenerationService {

    private final ScriptRepository scriptRepository;
    private final SceneRepository sceneRepository;
    private final FrameRepository frameRepository;
    private final FileStorageService fileStorageService;
    private final AiParserClient parserClient;
    private final AiImageClient imageClient;
    private final PromptCompilerService promptCompiler;

    public SceneGenerationService(ScriptRepository scriptRepository,
                                  SceneRepository sceneRepository,
                                  FrameRepository frameRepository,
                                  FileStorageService fileStorageService,
                                  AiParserClient parserClient,
                                  AiImageClient imageClient,
                                  PromptCompilerService promptCompiler) {
        this.scriptRepository = scriptRepository;
        this.sceneRepository = sceneRepository;
        this.frameRepository = frameRepository;
        this.fileStorageService = fileStorageService;
        this.parserClient = parserClient;
        this.imageClient = imageClient;
        this.promptCompiler = promptCompiler;
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

                // 1️⃣ Извлечение текста
                String text = fileStorageService.extractText(script.getFilePath());
                script.setTextExtracted(text);

                // 2️⃣ Парсинг сцен AI
                List<Scene> parsedScenes = parserClient.parseScenes(text);
                for (Scene scene : parsedScenes) {
                    scene.setScript(script);
                    scene.setStatus(SceneStatus.PARSED);
                }
                sceneRepository.saveAll(parsedScenes);
                script.setParsedJson(parserClient.getLastRawJson());
                script.setStatus(ScriptStatus.PARSED);
                scriptRepository.save(script);

                // 3️⃣ Генерация изображений
                script.setStatus(ScriptStatus.GENERATING);
                scriptRepository.save(script);
                for (Scene scene : parsedScenes) {
                    generateSceneFrame(scene, DetailLevel.SKETCH);
                }

                script.setStatus(ScriptStatus.READY);
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

        String prompt = promptCompiler.compile(scene, level);
        ImageResult result = imageClient.generate(prompt, level);

        Frame frame = new Frame();
        frame.setScene(scene);
        frame.setPrompt(prompt);
        frame.setDetailLevel(level);
        frame.setImageUrl(result.imageUrl());
        frame.setModel(result.model());
        frame.setSeed(result.seed());
        frame.setGenerationTime(Duration.between(start, Instant.now()));
        frameRepository.save(frame);

        scene.setStatus(SceneStatus.READY);
        sceneRepository.save(scene);
    }
}
