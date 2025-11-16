package ru.wink.winkaipreviz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.entity.SceneStatus;
import ru.wink.winkaipreviz.repository.SceneRepository;

import java.util.Optional;

/**
 * Transactional worker logic for a single scene parsing task.
 * Called from background worker threads managed by SceneParsingWorkerManager.
 */
@Service
public class SceneParsingWorkerService {

    private static final Logger log = LoggerFactory.getLogger(SceneParsingWorkerService.class);

    private final SceneRepository sceneRepository;
    private final OllamaScriptParserService ollamaScriptParserService;

    public SceneParsingWorkerService(SceneRepository sceneRepository,
                                     OllamaScriptParserService ollamaScriptParserService) {
        this.sceneRepository = sceneRepository;
        this.ollamaScriptParserService = ollamaScriptParserService;
    }

    @Transactional
    public void processTask(SceneParsingTask task) {
        Optional<Scene> optionalScene = sceneRepository.findById(task.getSceneId());
        if (optionalScene.isEmpty()) {
            log.warn("Scene not found for parsing. scriptId={}, sceneId={}", task.getScriptId(), task.getSceneId());
            return;
        }

        Scene scene = optionalScene.get();

        // Update status to PROCESSING
        scene.setStatus(SceneStatus.PROCESSING);
        sceneRepository.save(scene);

        String sceneText = scene.getDescription();
        if (sceneText == null || sceneText.isBlank()) {
            log.warn("Scene text is empty, marking as FAILED. sceneId={}", scene.getId());
            scene.setStatus(SceneStatus.FAILED);
            sceneRepository.save(scene);
            return;
        }

        try {
            // 1) Parse scene text into base JSON via Ollama
            String baseJson = ollamaScriptParserService.parseSceneTextToJson(sceneText);
            scene.setOriginalJson(baseJson);

            // 2) Map JSON fields back into the existing Scene entity
            ollamaScriptParserService.applySceneJsonToEntity(baseJson, scene);

            // 3) Mark as successfully parsed
            scene.setStatus(SceneStatus.PARSED);
            sceneRepository.save(scene);

            log.info("Scene parsed successfully by worker. sceneId={}", scene.getId());
        } catch (Exception e) {
            log.error("Error during scene parsing. sceneId={}, error={}", scene.getId(), e.getMessage(), e);
            scene.setStatus(SceneStatus.FAILED);
            sceneRepository.save(scene);
        }
    }
}


