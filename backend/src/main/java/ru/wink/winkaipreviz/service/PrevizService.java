package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wink.winkaipreviz.dto.*;
import ru.wink.winkaipreviz.entity.Frame;
import ru.wink.winkaipreviz.entity.DetailLevel;
import ru.wink.winkaipreviz.entity.GenerationPath;
import ru.wink.winkaipreviz.entity.LODProfile;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.entity.Script;
import ru.wink.winkaipreviz.entity.ScriptStatus;
import ru.wink.winkaipreviz.entity.SceneStatus;
import ru.wink.winkaipreviz.repository.FrameRepository;
import ru.wink.winkaipreviz.repository.SceneRepository;
import ru.wink.winkaipreviz.repository.ScriptRepository;
import ru.wink.winkaipreviz.repository.SceneVisualRepository;
import ru.wink.winkaipreviz.entity.SceneVisualEntity;
import ru.wink.winkaipreviz.entity.VisualStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PrevizService {

	private static final Logger log = LoggerFactory.getLogger(PrevizService.class);

	private final ScriptRepository scriptRepository;
	private final SceneRepository sceneRepository;
	private final FrameRepository frameRepository;
	private final ScriptProcessorClient scriptProcessorClient;
	private final AiImageClient aiImageClient;
	private final OllamaScriptParserService ollamaScriptParserService;
	private final SceneToFluxPromptService sceneToFluxPromptService;
	private final SceneVisualRepository sceneVisualRepository;
	private final SceneVisualService sceneVisualService;

	private final AsyncTaskExecutor taskExecutor;

    @Value("${app.parser.threshold:3}")
    private int parserThreshold;

    @Value("${app.parser.use-stub:false}")
    private boolean useStub;


    private final FileStorageService fileStorageService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public PrevizService(ScriptRepository scriptRepository, SceneRepository sceneRepository, FrameRepository frameRepository,
						 FileStorageService fileStorageService, ScriptProcessorClient scriptProcessorClient,
						 AiImageClient aiImageClient,
						 OllamaScriptParserService ollamaScriptParserService,
						 SceneToFluxPromptService sceneToFluxPromptService,
						 SceneVisualRepository sceneVisualRepository,
						 SceneVisualService sceneVisualService,
						 AsyncTaskExecutor taskExecutor) {
		this.scriptRepository = scriptRepository;
		this.sceneRepository = sceneRepository;
		this.frameRepository = frameRepository;
        this.fileStorageService = fileStorageService;
		this.scriptProcessorClient = scriptProcessorClient;
		this.aiImageClient = aiImageClient;
        this.ollamaScriptParserService = ollamaScriptParserService;
		this.sceneToFluxPromptService = sceneToFluxPromptService;
		this.sceneVisualRepository = sceneVisualRepository;
		this.sceneVisualService = sceneVisualService;
		this.taskExecutor = taskExecutor;
	}
	@Transactional
	public ScriptUploadResponse createScriptFromUpload(MultipartFile file) throws Exception {
        // 1) Сохраняем файл
        log.info("Uploading script: name={}, size={} bytes, contentType={}", file.getOriginalFilename(), file.getSize(), file.getContentType());
        String storedPath = fileStorageService.store(file);
        log.info("Stored uploaded file to {}", storedPath);

        // 2) Создаём сущность Script
        Script script = new Script();
        script.setFilename(file.getOriginalFilename());
        script.setStatus(ScriptStatus.UPLOADED);
        script.setFilePath(storedPath);
		script = scriptRepository.save(script);
		log.debug("Script entity created: id={}", script.getId());
		final java.util.UUID scheduledScriptId = script.getId();
		// Schedule background processing after transaction commit
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				taskExecutor.execute(() -> {
					try {
						processScriptInBackground(scheduledScriptId);
					} catch (Exception ex) {
						log.error("Background processing failed for scriptId={}: {}", scheduledScriptId, ex.getMessage(), ex);
					}
				});
			}
		});

		// 7) DTO-ответ (return early)
		ScriptUploadResponse resp = new ScriptUploadResponse();
		resp.setScriptId(script.getId().toString());
		resp.setFilename(script.getFilename());
		resp.setStatus(script.getStatus() == null ? null : script.getStatus().name());
		resp.setScenes(List.of());
		resp.setChunkFiles(List.of());
		log.info("Upload accepted for scriptId={}. Background processing scheduled.", script.getId());
		return resp;
    }

	@Transactional
	public void processScriptInBackground(UUID scriptId) {
		try {
			Script script = scriptRepository.findById(scriptId)
					.orElseThrow(() -> new IllegalArgumentException("Script not found: " + scriptId));
			script.setStatus(ScriptStatus.PARSING);
			scriptRepository.save(script);

		// 3) Получаем сцены напрямую из script-processor
			List<ScriptProcessorClient.SceneData> sceneDataList;
			try {
				log.info("(background) Requesting scenes from script-processor for scriptId={}", script.getId());
				sceneDataList = scriptProcessorClient.splitToScenes(java.nio.file.Path.of(script.getFilePath()));
				log.info("(background) Received {} scenes from script-processor for scriptId={}", sceneDataList.size(), script.getId());
			} catch (Exception ex) {
				log.error("(background) Scene extraction failed in script-processor for scriptId={}: {}", script.getId(), ex.getMessage(), ex);
				script.setStatus(ScriptStatus.FAILED);
				scriptRepository.save(script);
				return;
			}

		// 4) Создаём Scene объекты из данных script-processor
		List<Scene> parsedScenes = new ArrayList<>();
		StringBuilder allText = new StringBuilder();
		
		for (ScriptProcessorClient.SceneData sceneData : sceneDataList) {
			try {
				Scene scene = new Scene();
				scene.setScript(script);
				scene.setTitle(sceneData.slugline());
				scene.setLocation(sceneData.place());
				scene.setDescription(sceneData.text()); // Сохраняем полный текст сцены
				scene.setStatus(SceneStatus.PARSED);
				
				parsedScenes.add(scene);
				
				if (allText.length() > 0) {
					allText.append("\n\n");
				}
				allText.append(sceneData.text());
			} catch (Exception ex) {
				log.error("(background) Failed to create scene from sceneData for scriptId={}: {}", script.getId(), ex.getMessage(), ex);
			}
		}
		
		log.info("(background) Created {} scenes for scriptId={}", parsedScenes.size(), script.getId());
		sceneRepository.saveAll(parsedScenes);

		// 5) Сохраняем извлечённый текст (как конкатенацию сцен)
		if (allText.length() > 0) {
			script.setTextExtracted(allText.toString());
		}

			// 6) Обновляем статус
			script.setStatus(parsedScenes.isEmpty() ? ScriptStatus.FAILED : ScriptStatus.PARSED);
			scriptRepository.save(script);
			log.info("(background) Script {} status updated to {}", script.getId(), script.getStatus());
		} finally {
			// Файлы сохраняются в /data/uploads и не удаляются после обработки
			// для возможности повторной обработки или отладки
		}
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
	public List<SceneDto> ingestScenes(String scriptIdStr, List<IncomingSceneDto> incoming) {
		UUID scriptId = UUID.fromString(scriptIdStr);
		Script script = scriptRepository.findById(scriptId)
				.orElseThrow(() -> new IllegalArgumentException("Script not found: " + scriptIdStr));
		if (incoming == null || incoming.isEmpty()) {
			return List.of();
		}

		List<Scene> toSave = new ArrayList<>();
		for (IncomingSceneDto in : incoming) {
			// идемпотентность по externalId, если есть
			if (in.getExternalId() != null && !in.getExternalId().isBlank()) {
				if (sceneRepository.existsByScript_IdAndExternalId(script.getId(), in.getExternalId())) {
					continue;
				}
			} else {
				// иначе считаем хэш содержимого и проверяем дубликат
				String dedup = computeSceneDedup(in);
				if (sceneRepository.existsByScript_IdAndDedupHash(script.getId(), dedup)) {
					continue;
				}
			}

			Scene s = new Scene();
			s.setScript(script);
			s.setExternalId(in.getExternalId());
			s.setDedupHash(in.getExternalId() == null || in.getExternalId().isBlank() ? computeSceneDedup(in) : null);
			s.setTitle(in.getTitle());
			s.setLocation(in.getLocation());
			s.setDescription(in.getDescription());
			s.setSemanticSummary(in.getSemanticSummary());
			s.setTone(in.getTone());
			s.setStyle(in.getStyle());
			s.setCharacters(in.getCharacters() == null ? new ArrayList<>() : new ArrayList<>(in.getCharacters()));
			s.setProps(in.getProps() == null ? new ArrayList<>() : new ArrayList<>(in.getProps()));
			s.setStatus(SceneStatus.PARSED);
			toSave.add(s);
		}

		if (toSave.isEmpty()) return List.of();
		List<Scene> saved = sceneRepository.saveAll(toSave);

		List<SceneDto> out = new ArrayList<>();
		for (Scene s : saved) out.add(mapSceneWithFrames(s));
		return out;
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
		scene = sceneRepository.save(scene);
		return mapSceneWithFrames(scene);
	}

	/**
	 * Упрощённый сценарий "быстрой правки" сцены коротким текстом.
	 * На первом этапе просто обновляем description, а также tone/style, если пользователь их явно меняет.
	 * В дальнейшем сюда можно встроить LLM-пайплайн для автоматического обновления enriched JSON.
	 */
	@Transactional
	public SceneDto refineScene(String sceneIdStr, RefineSceneRequest req) {
		UUID sceneId = UUID.fromString(sceneIdStr);
		Scene scene = sceneRepository.findById(sceneId)
				.orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneIdStr));

		String instruction = req.getInstruction();
		if (instruction == null || instruction.isBlank()) {
			return mapSceneWithFrames(scene);
		}

		// На текущем этапе — минимальный, но предсказуемый behavior:
		// добавляем текст правки в конец описания сцены отдельным абзацем.
		String baseDescription = scene.getDescription() == null ? "" : scene.getDescription().trim();
		String refined = baseDescription.isEmpty()
				? instruction.trim()
				: baseDescription + "\n\n[Правка пользователя]: " + instruction.trim();
		scene.setDescription(refined);

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

	/**
	 * Собрать полные frame-card по всем кадрам сценария.
	 */
	@Transactional(readOnly = true)
	public List<FrameCardDto> getFrameCardsForScript(String scriptIdStr) {
		UUID scriptId = UUID.fromString(scriptIdStr);
		List<Scene> scenes = sceneRepository.findByScript_Id(scriptId);
		List<FrameCardDto> cards = new ArrayList<>();

		for (Scene scene : scenes) {
			UUID sceneId = scene.getId();
			List<Frame> frames = frameRepository.findByScene_IdOrderByCreatedAtDesc(sceneId);

			PromptSlotsDto slots = null;
			try {
				slots = sceneVisualService.getSlotsForScene(sceneId);
			} catch (Exception ignored) {
				// если enriched JSON не готов или LLM-пайплайн не запускался — слоты остаются null
			}

			for (Frame f : frames) {
				FrameTechMetaDto meta = null;
				if (f.getEnrichedJson() != null && !f.getEnrichedJson().isBlank()) {
					try {
						meta = objectMapper.readValue(f.getEnrichedJson(), FrameTechMetaDto.class);
					} catch (Exception ignored) {
						// старый формат или произвольный JSON — пропускаем meta
					}
				}

				FrameCardDto card = new FrameCardDto(
						f.getId().toString(),
						sceneId != null ? sceneId.toString() : null,
						scene.getScript() != null && scene.getScript().getId() != null
								? scene.getScript().getId().toString()
								: null,
						scene.getTitle(),
						scene.getLocation(),
						new ArrayList<>(scene.getCharacters() == null ? List.of() : scene.getCharacters()),
						new ArrayList<>(scene.getProps() == null ? List.of() : scene.getProps()),
						f.getDetailLevel() == null ? null : f.getDetailLevel().name().toLowerCase(),
						f.getGenerationPath() == null ? null : f.getGenerationPath().name().toLowerCase(),
						f.getPrompt(),
						f.getImageUrl(),
						f.getCreatedAt() == null ? null : f.getCreatedAt().toString(),
						f.getIsBest(),
						slots,
						meta
				);
				cards.add(card);
			}
		}

		return cards;
	}

	@Transactional
	public FrameDto generateFrame(String sceneIdStr, GenerateFrameRequest req) {
		UUID sceneId = UUID.fromString(sceneIdStr);
		Scene scene = sceneRepository.findById(sceneId).orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneIdStr));
		
		// Определяем LOD профиль из запроса
		LODProfile lodProfile = LODProfile.fromString(req.getDetailLevel());
		
		// Определяем путь генерации (progressive/direct)
		GenerationPath path = determineGenerationPath(req.getPath(), lodProfile);
		
		// Определяем DetailLevel для обратной совместимости
		DetailLevel level = switch (lodProfile) {
			case SKETCH -> DetailLevel.SKETCH;
			case MID -> DetailLevel.MID;
			case FINAL -> DetailLevel.FINAL;
			case DIRECT_FINAL -> DetailLevel.DIRECT_FINAL;
		};
		
		log.info("Generating frame: sceneId={}, lod={}, path={}", sceneId, lodProfile.getCode(), path);
		
		// Используем новый pipeline: base JSON → enriched JSON → Flux prompt
		String prompt;
		
		try {
			// Получаем или создаем enriched JSON и промпт
			SceneVisualEntity visual = sceneVisualRepository.findBySceneId(sceneId)
					.orElseGet(() -> {
						try {
							// Получаем base JSON сцены
							String baseJson = getBaseSceneJson(scene);
							
							// Запускаем pipeline обогащения
							SceneToFluxPromptService.FluxPromptResult result = 
									sceneToFluxPromptService.generateFromSceneJson(baseJson);
							
							// Сохраняем enriched JSON и промпт
							SceneVisualEntity newVisual = new SceneVisualEntity();
							newVisual.setSceneId(sceneId);
							newVisual.setEnrichedJson(result.enrichedJson());
							newVisual.setFluxPrompt(result.prompt());
							newVisual.setStatus(VisualStatus.PROMPT_READY);
							return sceneVisualRepository.save(newVisual);
						} catch (Exception e) {
							log.error("Failed to generate enriched JSON for sceneId={}: {}", sceneId, e.getMessage(), e);
							return null;
						}
					});
			
			if (visual != null && visual.getFluxPrompt() != null && !visual.getFluxPrompt().isBlank()) {
				prompt = visual.getFluxPrompt();
				log.info("Using cached enriched JSON and prompt for sceneId={}", sceneId);
			} else {
				throw new IllegalStateException("Failed to generate Flux prompt for sceneId=" + sceneId);
			}
		} catch (Exception e) {
			log.error("Error in LLM prompt pipeline for sceneId={}: {}", sceneId, e.getMessage(), e);
			// В крайнем случае генерируем простой промпт из данных сцены,
			// чтобы не блокировать пользователю генерацию картинки.
			StringBuilder sb = new StringBuilder();
			sb.append("Scene ").append(scene.getTitle() == null ? "" : scene.getTitle())
					.append(" / ").append(scene.getLocation() == null ? "" : scene.getLocation())
					.append(". ");
			if (scene.getDescription() != null) {
				sb.append(scene.getDescription());
			}
			prompt = sb.toString();
		}
		
		// Если пользователь предоставил свой промпт, используем его
		if (req.getPrompt() != null && !req.getPrompt().isBlank()) {
			prompt = req.getPrompt();
			log.info("Using user-provided prompt for sceneId={}", sceneId);
		}
		
		// Применяем параметры LOD профиля и генерируем кадр
		long startTime = System.currentTimeMillis();
		ru.wink.winkaipreviz.ai.ImageResult ai = aiImageClient.generateWithProfile(
				prompt, 
				lodProfile,
				req.getSeed(),
				req.getModel()
		);
		long generationTime = System.currentTimeMillis() - startTime;
		
		// Создаем Frame с метаданными
		Frame frame = new Frame();
		frame.setScene(scene);
		frame.setDetailLevel(level);
		frame.setGenerationPath(path);
		frame.setPrompt(prompt);
		frame.setSeed(ai != null ? ai.seed() : (req.getSeed() != null ? req.getSeed() : (int)(System.currentTimeMillis() % Integer.MAX_VALUE)));
		frame.setModel(ai != null ? ai.model() : (req.getModel() != null ? req.getModel() : "unknown"));
		frame.setImageUrl(ai != null ? ai.imageUrl() : ("http://localhost:8000/mock_" + lodProfile.getCode() + ".png"));
		frame.setGenerationMs(generationTime);
		
		// Создаем полные метаданные кадра (frame-card)
		FrameTechMetaDto meta = createFrameTechMeta(lodProfile, path, frame.getSeed(), generationTime, ai);
		try {
			frame.setEnrichedJson(objectMapper.writeValueAsString(meta));
		} catch (Exception e) {
			log.warn("Failed to serialize frame tech meta for sceneId={}: {}", sceneId, e.getMessage());
		}
		
		frame = frameRepository.save(frame);
		log.info("Generated frame: frameId={}, sceneId={}, lod={}, path={}, seed={}", 
				frame.getId(), sceneId, lodProfile.getCode(), path, frame.getSeed());
		return mapFrame(frame);
	}
	
	/**
	 * Генерирует кадр через progressive path: Sketch → Mid → Final.
	 * Если Sketch отсутствует, создает его сначала.
	 * Затем последовательно создает Mid из Sketch и Final из Mid через img2img.
	 * 
	 * @param sceneIdStr ID сцены
	 * @param req запрос на генерацию (может содержать targetLod для остановки на определенном уровне)
	 * @return финальный кадр (Final или указанный targetLod)
	 */
	@Transactional
	public FrameDto generateProgressiveFrame(String sceneIdStr, GenerateFrameRequest req) {
		UUID sceneId = UUID.fromString(sceneIdStr);
		Scene scene = sceneRepository.findById(sceneId)
				.orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneIdStr));
		
		log.info("Generating progressive frame: sceneId={}", sceneId);
		
		// Определяем целевой LOD (по умолчанию Final)
		String targetLodStr = req.getDetailLevel() != null && !req.getDetailLevel().isBlank() 
				? req.getDetailLevel() : "final";
		LODProfile targetLod = LODProfile.fromString(targetLodStr);
		
		// Проверяем, что целевой LOD поддерживает progressive path
		if (targetLod == LODProfile.DIRECT_FINAL || targetLod == LODProfile.SKETCH) {
			throw new IllegalArgumentException(
					"Progressive path не поддерживается для LOD: " + targetLod.getCode() + 
					". Используйте обычный generateFrame для Sketch или Direct Final.");
		}
		
		// Получаем или создаем промпт
		String prompt = getOrCreatePrompt(scene, sceneId, req);
		
		// Шаг 1: Проверяем наличие Sketch кадра, если нет - создаем
		Frame sketchFrame = findOrCreateSketchFrame(scene, sceneId, prompt, req.getSeed(), req.getModel());
		log.info("Sketch frame ready: frameId={}, imageUrl={}", sketchFrame.getId(), sketchFrame.getImageUrl());
		
		// Шаг 2: Создаем Mid из Sketch (если нужен)
		Frame midFrame = null;
		if (targetLod == LODProfile.MID || targetLod == LODProfile.FINAL) {
			midFrame = createMidFromSketch(scene, sketchFrame, prompt, req.getSeed(), req.getModel());
			log.info("Mid frame created: frameId={}, imageUrl={}", midFrame.getId(), midFrame.getImageUrl());
		}
		
		// Шаг 3: Создаем Final из Mid (если нужен)
		Frame finalFrame = null;
		if (targetLod == LODProfile.FINAL) {
			// Final всегда создается из Mid (если Mid был создан), иначе из Sketch
			Frame parentFrame = midFrame != null ? midFrame : sketchFrame;
			finalFrame = createFinalFromParent(scene, parentFrame, prompt, req.getSeed(), req.getModel());
			log.info("Final frame created: frameId={}, imageUrl={}, parentFrameId={}", 
					finalFrame.getId(), finalFrame.getImageUrl(), parentFrame.getId());
		}
		
		// Возвращаем финальный кадр (или Mid, если это был целевой LOD)
		Frame resultFrame = finalFrame != null ? finalFrame : (midFrame != null ? midFrame : sketchFrame);
		return mapFrame(resultFrame);
	}
	
	/**
	 * Получает или создает промпт для генерации.
	 */
	private String getOrCreatePrompt(Scene scene, UUID sceneId, GenerateFrameRequest req) {
		// Если пользователь предоставил промпт, используем его
		if (req.getPrompt() != null && !req.getPrompt().isBlank()) {
			return req.getPrompt();
		}
		
		// Иначе получаем из enriched JSON
		try {
			SceneVisualEntity visual = sceneVisualRepository.findBySceneId(sceneId)
					.orElseGet(() -> {
						try {
							String baseJson = getBaseSceneJson(scene);
							SceneToFluxPromptService.FluxPromptResult result = 
									sceneToFluxPromptService.generateFromSceneJson(baseJson);
							SceneVisualEntity newVisual = new SceneVisualEntity();
							newVisual.setSceneId(sceneId);
							newVisual.setEnrichedJson(result.enrichedJson());
							newVisual.setFluxPrompt(result.prompt());
							newVisual.setStatus(VisualStatus.PROMPT_READY);
							return sceneVisualRepository.save(newVisual);
						} catch (Exception e) {
							log.error("Failed to generate enriched JSON for sceneId={}: {}", sceneId, e.getMessage(), e);
							return null;
						}
					});
			
			if (visual != null && visual.getFluxPrompt() != null && !visual.getFluxPrompt().isBlank()) {
				return visual.getFluxPrompt();
			}
		} catch (Exception e) {
			log.warn("Failed to get prompt from enriched JSON: {}", e.getMessage());
		}
		
		// Fallback: простой промпт из данных сцены
		StringBuilder sb = new StringBuilder();
		sb.append("Scene ").append(scene.getTitle() == null ? "" : scene.getTitle())
				.append(" / ").append(scene.getLocation() == null ? "" : scene.getLocation())
				.append(". ");
		if (scene.getDescription() != null) {
			sb.append(scene.getDescription());
		}
		return sb.toString();
	}
	
	/**
	 * Находит существующий Sketch кадр или создает новый.
	 */
	private Frame findOrCreateSketchFrame(Scene scene, UUID sceneId, String prompt, Integer seed, String model) {
		// Ищем существующий Sketch кадр
		List<Frame> existingSketchFrames = frameRepository.findByScene_IdAndDetailLevelOrderByCreatedAtDesc(
				sceneId, DetailLevel.SKETCH);
		
		if (!existingSketchFrames.isEmpty()) {
			Frame existing = existingSketchFrames.get(0);
			log.info("Using existing Sketch frame: frameId={}", existing.getId());
			return existing;
		}
		
		// Создаем новый Sketch кадр
		log.info("Creating new Sketch frame for sceneId={}", sceneId);
		LODProfile sketchProfile = LODProfile.SKETCH;
		long startTime = System.currentTimeMillis();
		
		ru.wink.winkaipreviz.ai.ImageResult ai = aiImageClient.generateWithProfile(
				prompt, sketchProfile, seed, model);
		long generationTime = System.currentTimeMillis() - startTime;
		
		Frame sketchFrame = new Frame();
		sketchFrame.setScene(scene);
		sketchFrame.setDetailLevel(DetailLevel.SKETCH);
		sketchFrame.setGenerationPath(GenerationPath.PROGRESSIVE);
		sketchFrame.setPrompt(prompt);
		sketchFrame.setSeed(ai != null ? ai.seed() : (seed != null ? seed : (int)(System.currentTimeMillis() % Integer.MAX_VALUE)));
		sketchFrame.setModel(ai != null ? ai.model() : (model != null ? model : "unknown"));
		sketchFrame.setImageUrl(ai != null ? ai.imageUrl() : ("http://localhost:8000/mock_sketch.png"));
		sketchFrame.setGenerationMs(generationTime);
		
		FrameTechMetaDto meta = createFrameTechMeta(sketchProfile, GenerationPath.PROGRESSIVE, 
				sketchFrame.getSeed(), generationTime, ai);
		try {
			sketchFrame.setEnrichedJson(objectMapper.writeValueAsString(meta));
		} catch (Exception e) {
			log.warn("Failed to serialize Sketch frame tech meta: {}", e.getMessage());
		}
		
		return frameRepository.save(sketchFrame);
	}
	
	/**
	 * Создает Mid кадр из Sketch через img2img.
	 */
	private Frame createMidFromSketch(Scene scene, Frame sketchFrame, String prompt, Integer seed, String model) {
		log.info("Creating Mid frame from Sketch: sketchFrameId={}", sketchFrame.getId());
		LODProfile midProfile = LODProfile.MID;
		long startTime = System.currentTimeMillis();
		
		// Используем seed из Sketch для continuity, если не указан свой
		Integer midSeed = seed != null ? seed : sketchFrame.getSeed();
		Double denoise = midProfile.getDenoiseRecommended();
		
		ru.wink.winkaipreviz.ai.ImageResult ai = aiImageClient.generateImg2Img(
				prompt, midProfile, sketchFrame.getImageUrl(), denoise, midSeed, model);
		long generationTime = System.currentTimeMillis() - startTime;
		
		Frame midFrame = new Frame();
		midFrame.setScene(scene);
		midFrame.setParentFrame(sketchFrame);
		midFrame.setDetailLevel(DetailLevel.MID);
		midFrame.setGenerationPath(GenerationPath.PROGRESSIVE);
		midFrame.setPrompt(prompt);
		midFrame.setSeed(ai != null ? ai.seed() : midSeed);
		midFrame.setModel(ai != null ? ai.model() : (model != null ? model : "unknown"));
		midFrame.setImageUrl(ai != null ? ai.imageUrl() : ("http://localhost:8000/mock_mid.png"));
		midFrame.setGenerationMs(generationTime);
		
		FrameTechMetaDto meta = createFrameTechMeta(midProfile, GenerationPath.PROGRESSIVE, 
				midFrame.getSeed(), generationTime, ai);
		try {
			midFrame.setEnrichedJson(objectMapper.writeValueAsString(meta));
		} catch (Exception e) {
			log.warn("Failed to serialize Mid frame tech meta: {}", e.getMessage());
		}
		
		return frameRepository.save(midFrame);
	}
	
	/**
	 * Создает Final кадр из родительского (Mid или Sketch) через img2img.
	 */
	private Frame createFinalFromParent(Scene scene, Frame parentFrame, String prompt, Integer seed, String model) {
		log.info("Creating Final frame from parent: parentFrameId={}, parentLod={}", 
				parentFrame.getId(), parentFrame.getDetailLevel());
		LODProfile finalProfile = LODProfile.FINAL;
		long startTime = System.currentTimeMillis();
		
		// Используем seed из родительского кадра для continuity, если не указан свой
		Integer finalSeed = seed != null ? seed : parentFrame.getSeed();
		Double denoise = finalProfile.getDenoiseRecommended();
		
		ru.wink.winkaipreviz.ai.ImageResult ai = aiImageClient.generateImg2Img(
				prompt, finalProfile, parentFrame.getImageUrl(), denoise, finalSeed, model);
		long generationTime = System.currentTimeMillis() - startTime;
		
		Frame finalFrame = new Frame();
		finalFrame.setScene(scene);
		finalFrame.setParentFrame(parentFrame);
		finalFrame.setDetailLevel(DetailLevel.FINAL);
		finalFrame.setGenerationPath(GenerationPath.PROGRESSIVE);
		finalFrame.setPrompt(prompt);
		finalFrame.setSeed(ai != null ? ai.seed() : finalSeed);
		finalFrame.setModel(ai != null ? ai.model() : (model != null ? model : "unknown"));
		finalFrame.setImageUrl(ai != null ? ai.imageUrl() : ("http://localhost:8000/mock_final.png"));
		finalFrame.setGenerationMs(generationTime);
		
		FrameTechMetaDto meta = createFrameTechMeta(finalProfile, GenerationPath.PROGRESSIVE, 
				finalFrame.getSeed(), generationTime, ai);
		try {
			finalFrame.setEnrichedJson(objectMapper.writeValueAsString(meta));
		} catch (Exception e) {
			log.warn("Failed to serialize Final frame tech meta: {}", e.getMessage());
		}
		
		return frameRepository.save(finalFrame);
	}
	
	/**
	 * Определяет путь генерации на основе запроса и LOD профиля.
	 */
	private GenerationPath determineGenerationPath(String requestedPath, LODProfile lodProfile) {
		if (requestedPath != null && !requestedPath.isBlank()) {
			String normalized = requestedPath.trim().toLowerCase();
			return "progressive".equals(normalized) ? GenerationPath.PROGRESSIVE : GenerationPath.DIRECT;
		}
		
		// Автоматическое определение:
		// - DIRECT_FINAL всегда direct
		// - Остальные по умолчанию direct (progressive будет реализован позже)
		return lodProfile == LODProfile.DIRECT_FINAL ? GenerationPath.DIRECT : GenerationPath.DIRECT;
	}
	
	/**
	 * Создает FrameTechMetaDto с параметрами из LOD профиля.
	 */
	private FrameTechMetaDto createFrameTechMeta(LODProfile lodProfile, GenerationPath path, 
	                                             Integer seed, long generationTimeMs, 
	                                             ru.wink.winkaipreviz.ai.ImageResult aiResult) {
		// Извлекаем метаданные из ответа AI, если есть
		Integer steps = null;
		Double cfg = null;
		String sampler = null;
		String scheduler = null;
		String resolution = null;
		String vae = null;
		List<ControlMetaDto> controls = null;
		Img2ImgMetaDto img2img = null;
		StyleMetaDto style = null;
		RefinerMetaDto refiner = null;
		UpscaleMetaDto upscale = null;
		
		if (aiResult != null && aiResult.metaJson() != null && !aiResult.metaJson().isBlank()) {
			try {
				Map<String, Object> metaMap = objectMapper.readValue(aiResult.metaJson(), Map.class);
				steps = metaMap.get("steps") instanceof Number n ? n.intValue() : null;
				cfg = metaMap.get("cfg") instanceof Number n ? n.doubleValue() : null;
				sampler = metaMap.get("sampler") != null ? metaMap.get("sampler").toString() : null;
				scheduler = metaMap.get("scheduler") != null ? metaMap.get("scheduler").toString() : null;
				resolution = metaMap.get("resolution") != null ? metaMap.get("resolution").toString() : null;
				vae = metaMap.get("vae") != null ? metaMap.get("vae").toString() : null;
			} catch (Exception e) {
				log.warn("Failed to parse AI result meta JSON: {}", e.getMessage());
			}
		}
		
		// Применяем значения по умолчанию из LOD профиля, если не указаны
		if (steps == null) {
			steps = lodProfile.getStepsRecommended();
		}
		if (cfg == null) {
			cfg = lodProfile.getCfgRecommended();
		}
		if (resolution == null) {
			resolution = lodProfile.getDefaultResolution();
		}
		
		// Создаем img2img метаданные, если профиль использует img2img
		if (lodProfile.isImg2Img()) {
			Double denoise = lodProfile.getDenoiseRecommended();
			img2img = new Img2ImgMetaDto(denoise);
		}
		
		// Создаем стилевые метаданные с негативами из профиля
		style = new StyleMetaDto(null, lodProfile.getDefaultNegativesString());
		
		// Создаем refiner метаданные, если рекомендуется
		if (lodProfile.isRefinerRecommended()) {
			refiner = new RefinerMetaDto(true, 0.25); // дефолтный denoise для refiner
		}
		
		// Создаем upscale метаданные, если рекомендуется
		if (lodProfile.isUpscaleRecommended()) {
			upscale = new UpscaleMetaDto(1.5, 0.25); // дефолтный factor и denoise
		}
		
		return new FrameTechMetaDto(
				seed != null ? seed.longValue() : null,
				steps,
				cfg,
				sampler,
				scheduler,
				resolution,
				vae,
				controls,
				img2img,
				style,
				refiner,
				upscale,
				lodProfile.getCode(),
				path.name().toLowerCase(),
				null, // queueMs - будет заполнено при реализации очереди
				generationTimeMs,
				null  // vramGb - будет заполнено при реализации мониторинга
		);
	}
	
	/**
	 * Полный пайплайн обогащения сцены:
	 * scene text -> ollama -> json -> ollama -> enriched json -> ollama -> text prompt
	 * 
	 * @param sceneId ID сцены
	 * @return результат пайплайна с enriched JSON и prompt
	 */
	@Transactional
	public SceneToFluxPromptService.FluxPromptResult enrichScenePipeline(UUID sceneId) throws Exception {
		Scene scene = sceneRepository.findById(sceneId)
				.orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneId));
		
		// Шаг 1: Получаем текст сцены
		String sceneText = scene.getDescription();
		if (sceneText == null || sceneText.isBlank()) {
			throw new IllegalStateException("Scene text is empty for sceneId=" + sceneId);
		}
		
		log.info("Starting enrichment pipeline for sceneId={}", sceneId);
		
		// Шаг 2: Парсим текст сцены в JSON через Ollama
		String baseJson;
		if (scene.getOriginalJson() != null && !scene.getOriginalJson().isBlank()) {
			// Используем сохраненный JSON, если есть
			baseJson = scene.getOriginalJson();
			log.info("Using cached base JSON for sceneId={}", sceneId);
		} else {
			// Парсим текст сцены в JSON
			baseJson = ollamaScriptParserService.parseSceneTextToJson(sceneText);
			// Сохраняем base JSON в сцену
			scene.setOriginalJson(baseJson);
			sceneRepository.save(scene);
			log.info("Parsed scene text to JSON for sceneId={}", sceneId);
		}
		
		// Шаг 3: Обогащаем JSON и генерируем prompt
		SceneToFluxPromptService.FluxPromptResult result = 
				sceneToFluxPromptService.generateFromSceneJson(baseJson);
		
		// Шаг 4: Сохраняем enriched JSON и prompt в SceneVisualEntity
		SceneVisualEntity visual = sceneVisualRepository.findBySceneId(sceneId)
				.orElseGet(() -> {
					SceneVisualEntity newVisual = new SceneVisualEntity();
					newVisual.setSceneId(sceneId);
					return newVisual;
				});
		
		visual.setEnrichedJson(result.enrichedJson());
		visual.setFluxPrompt(result.prompt());
		visual.setStatus(VisualStatus.PROMPT_READY);
		sceneVisualRepository.save(visual);
		
		log.info("Enrichment pipeline completed for sceneId={}", sceneId);
		return result;
	}

	/**
	 * Получает base JSON сцены для pipeline обогащения.
	 * Использует сохраненный originalJson, если есть, иначе конвертирует Scene в JSON.
	 */
	private String getBaseSceneJson(Scene scene) throws Exception {
		// Если есть сохраненный исходный JSON от парсера, используем его
		if (scene.getOriginalJson() != null && !scene.getOriginalJson().isBlank()) {
			return scene.getOriginalJson();
		}
		
		// Иначе конвертируем Scene в JSON
		ObjectNode json = objectMapper.createObjectNode();
		json.put("scene_id", scene.getId() != null ? scene.getId().toString() : "");
		json.put("slugline_raw", scene.getTitle() != null ? scene.getTitle() : "");
		json.put("location", scene.getLocation() != null ? scene.getLocation() : "");
		json.put("description", scene.getDescription() != null ? scene.getDescription() : "");
		json.put("tone", scene.getTone() != null ? scene.getTone() : "");
		json.put("style", scene.getStyle() != null ? scene.getStyle() : "");
		
		if (scene.getCharacters() != null && !scene.getCharacters().isEmpty()) {
			json.set("characters", objectMapper.valueToTree(scene.getCharacters()));
		}
		if (scene.getProps() != null && !scene.getProps().isEmpty()) {
			json.set("props", objectMapper.valueToTree(scene.getProps()));
		}
		
		return objectMapper.writeValueAsString(json);
	}

	@Transactional
	public FrameDto regenerateFrame(String frameIdStr, RegenerateFrameRequest req) {
		UUID frameId = UUID.fromString(frameIdStr);
		Frame base = frameRepository.findById(frameId).orElseThrow(() -> new IllegalArgumentException("Frame not found: " + frameIdStr));
		Scene scene = base.getScene();
		UUID sceneId = scene.getId();
		DetailLevel level = toDetailLevel(req.getDetailLevel());
		log.info("Regenerating frame: baseFrameId={}, sceneId={}, level={}", frameId, sceneId, level);
		
		String prompt;
		
		// Если пользователь предоставил свой промпт, используем его
		if (req.getPrompt() != null && !req.getPrompt().isBlank()) {
			prompt = req.getPrompt();
		} else {
			// Иначе используем сохраненный промпт из enriched JSON или генерируем новый
			try {
				SceneVisualEntity visual = sceneVisualRepository.findBySceneId(sceneId).orElse(null);
				if (visual != null && visual.getFluxPrompt() != null && !visual.getFluxPrompt().isBlank()) {
					prompt = visual.getFluxPrompt();
					log.info("Using cached prompt from enriched JSON for sceneId={}", sceneId);
				} else {
					// Если нет кеша — пересобираем пайплайн LLM по base JSON
					log.warn("No cached enriched JSON found, regenerating prompt via LLM pipeline for sceneId={}", sceneId);
					String baseJson = getBaseSceneJson(scene);
					SceneToFluxPromptService.FluxPromptResult result =
							sceneToFluxPromptService.generateFromSceneJson(baseJson);

					SceneVisualEntity newVisual = visual != null ? visual : new SceneVisualEntity();
					newVisual.setSceneId(sceneId);
					newVisual.setEnrichedJson(result.enrichedJson());
					newVisual.setFluxPrompt(result.prompt());
					newVisual.setStatus(VisualStatus.PROMPT_READY);
					SceneVisualEntity saved = sceneVisualRepository.save(newVisual);
					prompt = saved.getFluxPrompt();
				}
			} catch (Exception e) {
				log.error("Error regenerating prompt via LLM pipeline for sceneId={}: {}", sceneId, e.getMessage(), e);
				// В крайнем случае используем простой текстовый промпт
				StringBuilder sb = new StringBuilder();
				sb.append("Scene ").append(scene.getTitle() == null ? "" : scene.getTitle())
						.append(" / ").append(scene.getLocation() == null ? "" : scene.getLocation())
						.append(". ");
				if (scene.getDescription() != null) {
					sb.append(scene.getDescription());
				}
				prompt = sb.toString();
			}
		}
		
		ru.wink.winkaipreviz.ai.ImageResult ai = aiImageClient.generate(prompt, level);
		Frame newFrame = new Frame();
		newFrame.setScene(scene);
		newFrame.setDetailLevel(level);
		// регенерация считаем прогрессивным путём
		newFrame.setGenerationPath(GenerationPath.PROGRESSIVE);
		newFrame.setPrompt(prompt);
		newFrame.setSeed(ai != null ? ai.seed() : 12345);
		newFrame.setModel(ai != null ? ai.model() : "unknown");
		newFrame.setImageUrl(ai != null ? ai.imageUrl() : ("http://localhost:8000/mock_" + level.name().toLowerCase() + ".png"));
		if (ai != null && ai.metaJson() != null && !ai.metaJson().isBlank()) {
			newFrame.setEnrichedJson(ai.metaJson());
		}
		newFrame = frameRepository.save(newFrame);
		log.info("Regenerated frame: newFrameId={}, baseFrameId={}, sceneId={}, level={}", newFrame.getId(), frameId, sceneId, level);
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
		dto.setSceneId(f.getScene() != null && f.getScene().getId() != null ? f.getScene().getId().toString() : null);
		dto.setImageUrl(f.getImageUrl());
		dto.setDetailLevel(f.getDetailLevel() == null ? null : f.getDetailLevel().name().toLowerCase());
		dto.setPath(f.getGenerationPath() == null ? null : f.getGenerationPath().name().toLowerCase());
		dto.setPrompt(f.getPrompt());
		dto.setSeed(f.getSeed());
		dto.setModel(f.getModel());
		dto.setCreatedAt(f.getCreatedAt() == null ? null : f.getCreatedAt().toString());

		if (f.getEnrichedJson() != null && !f.getEnrichedJson().isBlank()) {
			try {
				dto.setMeta(objectMapper.readValue(f.getEnrichedJson(), FrameTechMetaDto.class));
			} catch (Exception e) {
				// если старый формат или битый JSON — просто игнорируем meta
				dto.setMeta(null);
			}
		}

		return dto;
	}

	private static DetailLevel toDetailLevel(String lod) {
		if (lod == null || lod.isBlank()) return DetailLevel.MID;
		String v = lod.trim().toLowerCase();
		return switch (v) {
			case "sketch" -> DetailLevel.SKETCH;
			case "mid", "medium" -> DetailLevel.MID;
			case "final" -> DetailLevel.FINAL;
			case "direct_final", "direct-final", "directfinal" -> DetailLevel.DIRECT_FINAL;
			default -> throw new IllegalArgumentException("Unsupported detailLevel: " + lod);
		};
	}

	private static String computeSceneDedup(IncomingSceneDto in) {
		try {
			var md = java.security.MessageDigest.getInstance("SHA-256");
			String base = String.join("|",
					nonNull(in.getTitle()),
					nonNull(in.getLocation()),
					nonNull(in.getDescription()),
					nonNull(in.getSemanticSummary()),
					nonNull(in.getTone()),
					nonNull(in.getStyle()),
					String.join(",", in.getCharacters() == null ? List.of() : in.getCharacters()),
					String.join(",", in.getProps() == null ? List.of() : in.getProps())
			);
			byte[] digest = md.digest(base.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (Exception e) {
			// в крайнем случае — fallback на хэш строки
			return Integer.toHexString((nonNull(in.getTitle()) + nonNull(in.getLocation())).hashCode());
		}
	}

	private static String nonNull(String v) {
		return v == null ? "" : v;
	}

}