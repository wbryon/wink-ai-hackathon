package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.wink.winkaipreviz.dto.*;
import ru.wink.winkaipreviz.entity.*;
import ru.wink.winkaipreviz.repository.*;

import java.util.*;

@Service
public class PrevizService {

    private final ScriptRepository scriptRepository;
    private final SceneRepository sceneRepository;
    private final FrameRepository frameRepository;
    private final FileStorageService fileStorageService;
    private final SceneGenerationService sceneGenerationService;
    private final PromptCompilerService promptCompilerService;
    private final AiImageClient aiImageClient;

    public PrevizService(ScriptRepository scriptRepository,
                         SceneRepository sceneRepository,
                         FrameRepository frameRepository,
                         FileStorageService fileStorageService,
                         SceneGenerationService sceneGenerationService,
                         PromptCompilerService promptCompilerService,
                         AiImageClient aiImageClient) {
        this.scriptRepository = scriptRepository;
        this.sceneRepository = sceneRepository;
        this.frameRepository = frameRepository;
        this.fileStorageService = fileStorageService;
        this.sceneGenerationService = sceneGenerationService;
        this.promptCompilerService = promptCompilerService;
        this.aiImageClient = aiImageClient;
    }

    /**
     * Загрузка и первичная регистрация сценария
     */
    @Transactional
    public ScriptUploadResponse createScriptFromUpload(MultipartFile file) throws Exception {
        String storedPath = fileStorageService.store(file);

        Script script = new Script();
        script.setFilename(file.getOriginalFilename());
        script.setFilePath(storedPath);
        script.setStatus(ScriptStatus.UPLOADED);
        script = scriptRepository.save(script);

        // ✅ Теперь анализ и генерация выполняются асинхронно
        sceneGenerationService.processScriptAsync(script.getId());

        ScriptUploadResponse resp = new ScriptUploadResponse();
        resp.setScriptId(script.getId().toString());
        resp.setFilename(script.getFilename());
        resp.setStatus(script.getStatus().name());
        resp.setScenes(List.of());
        return resp;
    }

    @Transactional(readOnly = true)
    public List<SceneDto> getScenes(String scriptIdStr) {
        UUID scriptId = UUID.fromString(scriptIdStr);
        return sceneRepository.findByScript_Id(scriptId)
                .stream()
                .map(this::mapSceneWithFrames)
                .toList();
    }

    @Transactional
    public SceneDto updateScene(String sceneIdStr, UpdateSceneRequest req) {
        UUID sceneId = UUID.fromString(sceneIdStr);
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneIdStr));
        scene.setTitle(req.getTitle());
        scene.setLocation(req.getLocation());
        scene.setDescription(req.getDescription());
        scene.setTone(req.getTone());
        scene.setStyle(req.getStyle());
        scene.setSemanticSummary(req.getSemanticSummary());
        scene.setCharacters(req.getCharacters() == null ? new ArrayList<>() : new ArrayList<>(req.getCharacters()));
        scene.setProps(req.getProps() == null ? new ArrayList<>() : new ArrayList<>(req.getProps()));
        scene = sceneRepository.save(scene);
        return mapSceneWithFrames(scene);
    }

    @Transactional
    public boolean deleteScene(String sceneIdStr) {
        UUID sceneId = UUID.fromString(sceneIdStr);
        if (!sceneRepository.existsById(sceneId)) return false;
        sceneRepository.deleteById(sceneId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<FrameDto> getFrames(String sceneIdStr) {
        UUID sceneId = UUID.fromString(sceneIdStr);
        return frameRepository.findByScene_IdOrderByCreatedAtDesc(sceneId)
                .stream()
                .map(this::mapFrame)
                .toList();
    }

    @Transactional
    public FrameDto generateFrame(String sceneIdStr, GenerateFrameRequest req) {
        UUID sceneId = UUID.fromString(sceneIdStr);
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneIdStr));
        DetailLevel level = parseLevel(req.getDetailLevel());
        String prompt = promptCompilerService.compile(scene, level);
        var result = aiImageClient.generate(prompt, level);

        Frame frame = new Frame();
        frame.setScene(scene);
        frame.setDetailLevel(level);
        frame.setPrompt(prompt);
        frame.setSeed(result.seed());
        frame.setModel(result.model());
        frame.setImageUrl(result.imageUrl());
        frame = frameRepository.save(frame);
        return mapFrame(frame);
    }

    @Transactional
    public FrameDto regenerateFrame(String frameIdStr, RegenerateFrameRequest req) {
        UUID frameId = UUID.fromString(frameIdStr);
        Frame base = frameRepository.findById(frameId)
                .orElseThrow(() -> new IllegalArgumentException("Frame not found: " + frameIdStr));
        Scene scene = base.getScene();
        DetailLevel level = parseLevel(req.getDetailLevel());
        String prompt = (req.getPrompt() != null && !req.getPrompt().isBlank())
                ? req.getPrompt()
                : promptCompilerService.compile(scene, level);
        var result = aiImageClient.generate(prompt, level);

        Frame newFrame = new Frame();
        newFrame.setScene(scene);
        newFrame.setDetailLevel(level);
        newFrame.setPrompt(prompt);
        newFrame.setSeed(result.seed());
        newFrame.setModel(result.model());
        newFrame.setImageUrl(result.imageUrl());
        newFrame = frameRepository.save(newFrame);
        return mapFrame(newFrame);
    }

    private DetailLevel parseLevel(String s) {
        if (s == null) return DetailLevel.MID;
        try {
            return DetailLevel.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DetailLevel.MID;
        }
    }

    private SceneDto mapSceneWithFrames(Scene s) {
        SceneDto dto = new SceneDto();
        dto.setId(s.getId().toString());
        dto.setTitle(s.getTitle());
        dto.setLocation(s.getLocation());
        dto.setCharacters(new ArrayList<>(s.getCharacters()));
        dto.setProps(new ArrayList<>(s.getProps()));
        dto.setDescription(s.getDescription());
        dto.setTone(s.getTone());
        dto.setStyle(s.getStyle());
        dto.setStatus(s.getStatus().name());

        List<FrameDto> frameDtos = frameRepository.findByScene_IdOrderByCreatedAtDesc(s.getId())
                .stream()
                .map(this::mapFrame)
                .toList();

        dto.setGeneratedFrames(frameDtos);
        dto.setCurrentFrame(frameDtos.isEmpty() ? null : frameDtos.get(0));
        return dto;
    }

    private FrameDto mapFrame(Frame f) {
        FrameDto dto = new FrameDto();
        dto.setId(f.getId().toString());
        dto.setImageUrl(f.getImageUrl());
        dto.setDetailLevel(f.getDetailLevel().name());
        dto.setPrompt(f.getPrompt());
        dto.setSeed(f.getSeed());
        dto.setModel(f.getModel());
        dto.setCreatedAt(f.getCreatedAt().toString());
        return dto;
    }
}
