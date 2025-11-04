package ru.wink.winkaipreviz.service;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wink.winkaipreviz.dto.*;
import ru.wink.winkaipreviz.entity.Frame;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.entity.Script;
import ru.wink.winkaipreviz.parser.RuleParser;
import ru.wink.winkaipreviz.repository.FrameRepository;
import ru.wink.winkaipreviz.repository.SceneRepository;
import ru.wink.winkaipreviz.repository.ScriptRepository;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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
    private final RuleParser ruleParser;
    private final Tika tika;
    private final AiParserClient aiParserClient;

    @Value("${app.parser.threshold:3}")
    private int parserThreshold;

    @Value("${app.parser.use-stub:false}")
    private boolean useStub;


    private final FileStorageService fileStorageService;

    public PrevizService(ScriptRepository scriptRepository, SceneRepository sceneRepository, FrameRepository frameRepository,
                         FileStorageService fileStorageService, RuleParser ruleParser, AiParserClient aiParserClient) {
		this.scriptRepository = scriptRepository;
		this.sceneRepository = sceneRepository;
		this.frameRepository = frameRepository;
        this.fileStorageService = fileStorageService;
        this.ruleParser = ruleParser;
        this.tika = new Tika();
        this.aiParserClient = aiParserClient;
	}
    @Transactional
    public ScriptUploadResponse createScriptFromUpload(MultipartFile file) throws Exception {
        // Сохраняем файл
        String storedPath = fileStorageService.store(file);

        // Создаём сущность Script
        Script script = new Script();
        script.setFilename(file.getOriginalFilename());
        script.setStatus("UPLOADED");
        script.setFilePath(storedPath);
        script = scriptRepository.save(script);

        // Извлекаем текст (через Apache Tika)
        String text = tika.parseToString(file.getInputStream());

        // Парсим сцены из текста
        List<Scene> scenes = ruleParser.parse(text);

        // Fallback к AI, если недостаточно сцен
        if (scenes == null) scenes = new ArrayList<>();
        if (scenes.size() < parserThreshold) {
            List<Scene> aiScenes = aiParserClient.parse(text);
            if (aiScenes != null && aiScenes.size() >= parserThreshold) {
                scenes = aiScenes;
                script.setStatus("PARSED_AI");
            }
        }

        // Если ничего не удалось — опциональные заглушки (можно отключить через конфиг)
        if (scenes.isEmpty()) {
            if (useStub) {
                scenes = createStubScenes(script);
                script.setStatus("PARSED_WITH_STUB");
            } else {
                script.setStatus("PARSED_EMPTY");
            }
        } else if (!"PARSED_AI".equals(script.getStatus())) {
            script.setStatus("PARSED");
        }

        // Привязываем сцены к сценарию и сохраняем
        for (Scene scene : scenes) {
            scene.setScript(script);
        }
        sceneRepository.saveAll(scenes);

        // Собираем DTO-ответ
        ScriptUploadResponse resp = new ScriptUploadResponse();
        resp.setScriptId(script.getId().toString());
        resp.setFilename(script.getFilename());
        resp.setStatus(script.getStatus());

        List<SceneDto> sceneDtos = new ArrayList<>();
        for (Scene s : scenes) sceneDtos.add(mapSceneWithFrames(s));
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
		String lod = req.getDetailLevel() == null ? "medium" : req.getDetailLevel();
		AiGenerationClient.GenerateResponse ai = aiGenerationClient.generate(scene.getId(), null, lod);
		Frame frame = new Frame();
		frame.setScene(scene);
		frame.setDetailLevel(lod);
		frame.setPrompt(null);
		frame.setSeed(ai != null && ai.seed != null ? ai.seed : 12345);
		frame.setImageUrl(ai != null && ai.imageUrl != null ? ai.imageUrl : ("http://localhost:8000/mock_" + lod + ".png"));
		frame.setCreatedAt(Instant.now());
		frame = frameRepository.save(frame);
		return mapFrame(frame);
	}

	@Transactional
	public FrameDto regenerateFrame(String frameIdStr, RegenerateFrameRequest req) {
		UUID frameId = UUID.fromString(frameIdStr);
		Frame base = frameRepository.findById(frameId).orElseThrow(() -> new IllegalArgumentException("Frame not found: " + frameIdStr));
		String lod = req.getDetailLevel() == null ? "medium" : req.getDetailLevel();
		AiGenerationClient.GenerateResponse ai = aiGenerationClient.generate(base.getScene().getId(), req.getPrompt(), lod);
		Frame newFrame = new Frame();
		newFrame.setScene(base.getScene());
		newFrame.setDetailLevel(lod);
		newFrame.setPrompt(req.getPrompt());
		newFrame.setSeed(ai != null && ai.seed != null ? ai.seed : 12345);
		newFrame.setImageUrl(ai != null && ai.imageUrl != null ? ai.imageUrl : ("http://localhost:8000/mock_" + lod + ".png"));
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