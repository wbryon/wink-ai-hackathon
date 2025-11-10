package ru.wink.winkaipreviz.service;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
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
	private final PromptCompilerClient promptCompilerService;
    private final AiImageClient aiImageClient;

    public PrevizService(ScriptRepository scriptRepository,
                         SceneRepository sceneRepository,
                         FrameRepository frameRepository,
                         FileStorageService fileStorageService,
                         SceneGenerationService sceneGenerationService,
						 PromptCompilerClient promptCompilerService,
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

        // Анализ и генерация выполняются асинхронно
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
        UUID scriptId = toUuid(scriptIdStr);
        return sceneRepository.findByScript_Id(scriptId)
                .stream()
                .map(this::mapSceneWithFrames)
                .toList();
    }

    @Transactional
    public SceneDto updateScene(String sceneIdStr, UpdateSceneRequest req) {
        UUID sceneId = toUuid(sceneIdStr);
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
        UUID sceneId = toUuid(sceneIdStr);
        if (!sceneRepository.existsById(sceneId)) return false;
        sceneRepository.deleteById(sceneId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<FrameDto> getFrames(String sceneIdStr) {
        UUID sceneId = toUuid(sceneIdStr);
        return frameRepository.findByScene_IdOrderByCreatedAtDesc(sceneId)
                .stream()
                .map(this::mapFrame)
                .toList();
    }

    @Transactional
    public FrameDto generateFrame(String sceneIdStr, GenerateFrameRequest req) {
        UUID sceneId = toUuid(sceneIdStr);
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneIdStr));
        DetailLevel level = parseLevel(req.getDetailLevel());
        String prompt = promptCompilerService.compile(scene, level);

        return createFrame(scene, prompt, level);
    }

    @Transactional
    public FrameDto regenerateFrame(String frameIdStr, RegenerateFrameRequest req) {
        UUID frameId = toUuid(frameIdStr);
        Frame base = frameRepository.findById(frameId)
                .orElseThrow(() -> new IllegalArgumentException("Frame not found: " + frameIdStr));
        Scene scene = base.getScene();
        DetailLevel level = parseLevel(req.getDetailLevel());
        String prompt = (req.getPrompt() != null && !req.getPrompt().isBlank())
                ? req.getPrompt()
                : promptCompilerService.compile(scene, level);

        return createFrame(scene, prompt, level);
    }

    /**
     * Приём распарсенных сцен от text-ai (webhook): сохраняем сцены без автогенерации кадров.
     */
    @Transactional
    public List<SceneDto> ingestScenes(String scriptIdStr, List<IncomingSceneDto> incoming) {
        UUID scriptId = toUuid(scriptIdStr);
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("Script not found: " + scriptIdStr));

        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }

        List<Scene> toSave = new ArrayList<>();
        for (IncomingSceneDto in : incoming) {
            // 1) Идемпотентность по externalId
            String extId = in.getExternalId();
            if (extId != null && !extId.isBlank()) {
                if (sceneRepository.existsByScript_IdAndExternalId(scriptId, extId)) {
                    continue; // дубль
                }
            } else {
                // 2) Идемпотентность по хэшу содержимого
                String hash = computeSceneHash(in);
                if (sceneRepository.existsByScript_IdAndDedupHash(scriptId, hash)) {
                    continue; // дубль
                }
            }

            Scene s = new Scene();
            s.setScript(script);
            s.setExternalId(extId);
            if (extId == null || extId.isBlank()) {
                s.setDedupHash(computeSceneHash(in));
            }
            s.setTitle(in.getTitle());
            s.setLocation(in.getLocation());
            // semanticSummary в приоритете, иначе оригинальное описание
            s.setSemanticSummary(in.getSemanticSummary());
            s.setDescription(in.getDescription());
            s.setTone(in.getTone());
            s.setStyle(in.getStyle());
            s.setCharacters(in.getCharacters() == null ? new ArrayList<>() : new ArrayList<>(in.getCharacters()));
            s.setProps(in.getProps() == null ? new ArrayList<>() : new ArrayList<>(in.getProps()));
            s.setStatus(SceneStatus.PARSED);
            toSave.add(s);
        }

        List<Scene> saved = sceneRepository.saveAll(toSave);

        // Обновляем статус сценария как минимум до PARSED (если был загружен)
        if (script.getStatus() == ScriptStatus.UPLOADED) {
            script.setStatus(ScriptStatus.PARSED);
            scriptRepository.save(script);
        }

        return saved.stream().map(this::mapSceneWithFrames).toList();
    }

    private static String computeSceneHash(IncomingSceneDto in) {
        // Каноническое представление для хэширования (без внешнего id)
        StringBuilder sb = new StringBuilder();
        sb.append(nullToEmpty(in.getTitle()).trim()).append('|');
        sb.append(nullToEmpty(in.getLocation()).trim()).append('|');
        sb.append(nullToEmpty(in.getSemanticSummary()).trim()).append('|');
        sb.append(nullToEmpty(in.getDescription()).trim()).append('|');
        // Упорядочим списки для стабильности
        List<String> chars = in.getCharacters() == null ? List.of() : new ArrayList<>(in.getCharacters());
        chars.replaceAll(s -> s == null ? "" : s.trim());
        chars.sort(String::compareTo);
        sb.append(String.join(",", chars)).append('|');
        List<String> props = in.getProps() == null ? List.of() : new ArrayList<>(in.getProps());
        props.replaceAll(s -> s == null ? "" : s.trim());
        props.sort(String::compareTo);
        sb.append(String.join(",", props)).append('|');
        sb.append(nullToEmpty(in.getTone()).trim()).append('|');
        sb.append(nullToEmpty(in.getStyle()).trim());
        return sha256Hex(sb.toString());
    }

    private static String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String h = Integer.toHexString(b & 0xff);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            // В маловероятном случае — возвращаем пустую строку, чтобы не завалить приём
            return "";
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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
        dto.setCurrentFrame(frameDtos.isEmpty() ? null : frameDtos.getFirst());
        return dto;
    }

    private FrameDto createFrame(Scene scene, String prompt, DetailLevel level) {
        var result = aiImageClient.generate(prompt, level);
        Frame frame = new Frame();
        frame.setScene(scene);
        frame.setDetailLevel(level);
        frame.setPrompt(prompt);
        frame.setSeed(result.seed());
        frame.setModel(result.model());
        frame.setImageUrl(result.imageUrl());
        return mapFrame(frameRepository.save(frame));
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

    @NonNull
    private static UUID toUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID: " + id);
        }
    }
}
