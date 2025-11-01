package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wink.winkaipreviz.dto.*;
import ru.wink.winkaipreviz.entity.Frame;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.entity.Script;
import ru.wink.winkaipreviz.repository.FrameRepository;
import ru.wink.winkaipreviz.repository.SceneRepository;
import ru.wink.winkaipreviz.repository.ScriptRepository;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PrevizService {

	private final ScriptRepository scriptRepository;
	private final SceneRepository sceneRepository;
	private final FrameRepository frameRepository;

    private final FileStorageService fileStorageService;

    public PrevizService(ScriptRepository scriptRepository, SceneRepository sceneRepository, FrameRepository frameRepository, FileStorageService fileStorageService) {
		this.scriptRepository = scriptRepository;
		this.sceneRepository = sceneRepository;
		this.frameRepository = frameRepository;
        this.fileStorageService = fileStorageService;
	}

	@Transactional
    public ScriptUploadResponse createScriptFromUpload(MultipartFile file) throws Exception {
        String storedPath = fileStorageService.store(file);

        Script script = new Script();
        script.setFilename(file.getOriginalFilename());
        script.setStatus("UPLOADED");
        script.setFilePath(storedPath);
        script = scriptRepository.save(script);

        // Заглушка парсинга: создаём несколько сцен для продолжения потока
        List<Scene> stubScenes = createStubScenes(script);
        sceneRepository.saveAll(stubScenes);

        ScriptUploadResponse resp = new ScriptUploadResponse();
        resp.setScriptId(script.getId().toString());
        resp.setFilename(script.getFilename());
        resp.setStatus(script.getStatus());
        List<SceneDto> sceneDtos = new ArrayList<>();
        for (Scene s : stubScenes) sceneDtos.add(mapSceneWithFrames(s));
        resp.setScenes(sceneDtos);
        return resp;
	}

    private List<Scene> createStubScenes(Script script) {
        List<Scene> list = new ArrayList<>();
        Scene s1 = new Scene();
        s1.setScript(script);
        s1.setTitle("EXT. SCHOOL YARD - MORNING");
        s1.setLocation("Школьный двор, утро");
        s1.setCharacters(List.of("Маша", "Подруга"));
        s1.setProps(List.of("Рюкзак"));
        s1.setDescription("Маша встречается с подругой у входа в школу.");
        list.add(s1);

        Scene s2 = new Scene();
        s2.setScript(script);
        s2.setTitle("INT. CLASSROOM - DAY");
        s2.setLocation("Класс, день");
        s2.setCharacters(List.of("Учитель", "Класс"));
        s2.setProps(List.of("Доска", "Мел"));
        s2.setDescription("Учитель начинает урок, дети тихо переговариваются.");
        list.add(s2);
        return list;
    }

	@Transactional(readOnly = true)
	public List<SceneDto> getScenes(String scriptIdStr) {
		UUID scriptId = UUID.fromString(scriptIdStr);
		List<Scene> scenes = sceneRepository.findByScript_Id(scriptId);
		List<SceneDto> result = new ArrayList<>();
		for (Scene s : scenes) {
			result.add(mapSceneWithFrames(s));
		}
		return result;
	}

	@Transactional
	public SceneDto addScene(String scriptIdStr, AddSceneRequest req) {
		UUID scriptId = UUID.fromString(scriptIdStr);
		Script script = scriptRepository.findById(scriptId).orElseThrow(() -> new IllegalArgumentException("Script not found: " + scriptIdStr));
		Scene scene = new Scene();
		scene.setScript(script);
		scene.setTitle(req.getTitle());
		scene.setLocation(req.getLocation());
		scene.setCharacters(req.getCharacters() == null ? new ArrayList<>() : new ArrayList<>(req.getCharacters()));
		scene.setProps(req.getProps() == null ? new ArrayList<>() : new ArrayList<>(req.getProps()));
		scene.setDescription(req.getDescription());
		scene = sceneRepository.save(scene);
		return mapSceneWithFrames(scene);
	}

	@Transactional
	public SceneDto updateScene(String sceneIdStr, UpdateSceneRequest req) {
		UUID sceneId = UUID.fromString(sceneIdStr);
		Scene scene = sceneRepository.findById(sceneId).orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneIdStr));
		scene.setTitle(req.getTitle());
		scene.setLocation(req.getLocation());
		scene.setCharacters(req.getCharacters() == null ? new ArrayList<>() : new ArrayList<>(req.getCharacters()));
		scene.setProps(req.getProps() == null ? new ArrayList<>() : new ArrayList<>(req.getProps()));
		scene.setDescription(req.getDescription());
		scene.setPrompt(req.getPrompt());
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
		List<Frame> frames = frameRepository.findByScene_IdOrderByCreatedAtDesc(sceneId);
		List<FrameDto> result = new ArrayList<>();
		for (Frame f : frames) {
			result.add(mapFrame(f));
		}
		return result;
	}

	@Transactional
	public FrameDto generateFrame(String sceneIdStr, GenerateFrameRequest req) {
		UUID sceneId = UUID.fromString(sceneIdStr);
		Scene scene = sceneRepository.findById(sceneId).orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneIdStr));
		Frame frame = new Frame();
		frame.setScene(scene);
		frame.setDetailLevel(req.getDetailLevel());
		frame.setPrompt(null);
		frame.setSeed(12345);
		frame.setImageUrl("http://localhost:8000/mock_" + (req.getDetailLevel() == null ? "medium" : req.getDetailLevel()) + ".png");
		frame.setCreatedAt(Instant.now());
		frame = frameRepository.save(frame);
		return mapFrame(frame);
	}

	@Transactional
	public FrameDto regenerateFrame(String frameIdStr, RegenerateFrameRequest req) {
		UUID frameId = UUID.fromString(frameIdStr);
		Frame base = frameRepository.findById(frameId).orElseThrow(() -> new IllegalArgumentException("Frame not found: " + frameIdStr));
		Frame newFrame = new Frame();
		newFrame.setScene(base.getScene());
		newFrame.setDetailLevel(req.getDetailLevel());
		newFrame.setPrompt(req.getPrompt());
		newFrame.setSeed(12345);
		newFrame.setImageUrl("http://localhost:8000/mock_" + (req.getDetailLevel() == null ? "medium" : req.getDetailLevel()) + ".png");
		newFrame.setCreatedAt(Instant.now());
		newFrame = frameRepository.save(newFrame);
		return mapFrame(newFrame);
	}

	private SceneDto mapSceneWithFrames(Scene s) {
		SceneDto dto = new SceneDto();
		dto.setId(s.getId().toString());
		dto.setTitle(s.getTitle());
		dto.setLocation(s.getLocation());
		dto.setCharacters(new ArrayList<>(s.getCharacters()));
		dto.setProps(new ArrayList<>(s.getProps()));
		dto.setDescription(s.getDescription());
		dto.setPrompt(s.getPrompt());
		List<Frame> frames = frameRepository.findByScene_IdOrderByCreatedAtDesc(s.getId());
		List<FrameDto> frameDtos = new ArrayList<>();
		for (Frame f : frames) {
			frameDtos.add(mapFrame(f));
		}
		dto.setGeneratedFrames(frameDtos);
		dto.setCurrentFrame(frameDtos.isEmpty() ? null : frameDtos.get(0));
		return dto;
	}

	private FrameDto mapFrame(Frame f) {
		FrameDto dto = new FrameDto();
		dto.setId(f.getId().toString());
		dto.setImageUrl(f.getImageUrl());
		dto.setDetailLevel(f.getDetailLevel());
		dto.setPrompt(f.getPrompt());
		dto.setSeed(f.getSeed());
		dto.setCreatedAt(f.getCreatedAt() == null ? null : f.getCreatedAt().toString());
		return dto;
	}
}


