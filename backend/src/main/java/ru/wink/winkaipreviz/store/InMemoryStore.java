package ru.wink.winkaipreviz.store;

import org.springframework.stereotype.Component;
import ru.wink.winkaipreviz.dto.AddSceneRequest;
import ru.wink.winkaipreviz.dto.FrameDto;
import ru.wink.winkaipreviz.dto.SceneDto;
import ru.wink.winkaipreviz.dto.UpdateSceneRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryStore {

	private final Map<String, ScriptRecord> scriptsById = new ConcurrentHashMap<>();
	private final Map<String, SceneDto> scenesById = new ConcurrentHashMap<>();
	private final Map<String, List<FrameDto>> framesBySceneId = new ConcurrentHashMap<>();
	private final Map<String, FrameDto> framesById = new ConcurrentHashMap<>();
	private final Map<String, String> sceneIdToScriptId = new ConcurrentHashMap<>();

	public synchronized String createScript(String filename, List<SceneDto> initialScenes) {
		String scriptId = generateScriptId();
		ScriptRecord script = new ScriptRecord();
		script.id = scriptId;
		script.filename = filename;
		script.status = "UPLOADED";
		script.sceneIds = new ArrayList<>();

		if (initialScenes != null) {
			for (SceneDto scene : initialScenes) {
				SceneDto created = createSceneInternal(scriptId, scene);
				script.sceneIds.add(created.getId());
			}
		}

		scriptsById.put(scriptId, script);
		return scriptId;
	}

	public Optional<ScriptRecord> getScript(String scriptId) {
		return Optional.ofNullable(scriptsById.get(scriptId));
	}

	public synchronized SceneDto addScene(String scriptId, AddSceneRequest req) {
		ScriptRecord script = requireScript(scriptId);
		SceneDto scene = new SceneDto();
		scene.setTitle(req.getTitle());
		scene.setLocation(req.getLocation());
		scene.setCharacters(nullSafeList(req.getCharacters()));
		scene.setProps(nullSafeList(req.getProps()));
		scene.setDescription(req.getDescription());

		SceneDto created = createSceneInternal(scriptId, scene);
		script.sceneIds.add(created.getId());
		return deepCopy(created);
	}

	public synchronized SceneDto updateScene(String sceneId, UpdateSceneRequest req) {
		SceneDto existing = requireScene(sceneId);
		existing.setTitle(req.getTitle());
		existing.setLocation(req.getLocation());
		existing.setCharacters(nullSafeList(req.getCharacters()));
		existing.setProps(nullSafeList(req.getProps()));
		existing.setDescription(req.getDescription());
		return deepCopy(existing);
	}

	public synchronized boolean deleteScene(String scriptId, String sceneId) {
		ScriptRecord script = requireScript(scriptId);
		SceneDto removed = scenesById.remove(sceneId);
		framesBySceneId.remove(sceneId);
		script.sceneIds.remove(sceneId);
		sceneIdToScriptId.remove(sceneId);
		return removed != null;
	}

	public List<SceneDto> getScenesByScript(String scriptId) {
		ScriptRecord script = requireScript(scriptId);
		List<SceneDto> result = new ArrayList<>();
		for (String sceneId : script.sceneIds) {
			SceneDto scene = scenesById.get(sceneId);
			if (scene != null) {
				result.add(deepCopy(scene));
			}
		}
		return result;
	}

	public synchronized FrameDto addFrameToScene(String sceneId, FrameDto frame) {
		SceneDto scene = requireScene(sceneId);
		String frameId = generateFrameId();
		FrameDto toSave = new FrameDto();
		toSave.setId(frameId);
		toSave.setImageUrl(frame.getImageUrl());
		toSave.setDetailLevel(frame.getDetailLevel());
		toSave.setPrompt(frame.getPrompt());
		toSave.setSeed(frame.getSeed());
		toSave.setCreatedAt(Optional.ofNullable(frame.getCreatedAt()).orElse(Instant.now().toString()));

		framesById.put(frameId, toSave);
		framesBySceneId.computeIfAbsent(sceneId, k -> new ArrayList<>()).add(toSave);
		scene.setCurrentFrame(toSave);
		scene.setGeneratedFrames(Collections.unmodifiableList(new ArrayList<>(framesBySceneId.get(sceneId))));
		return deepCopy(toSave);
	}

	public List<FrameDto> getFramesForScene(String sceneId) {
		requireScene(sceneId);
		List<FrameDto> list = framesBySceneId.getOrDefault(sceneId, Collections.emptyList());
		List<FrameDto> copy = new ArrayList<>();
		for (FrameDto f : list) {
			copy.add(deepCopy(f));
		}
		return copy;
	}

	public Optional<FrameDto> getFrame(String frameId) {
		FrameDto f = framesById.get(frameId);
		return Optional.ofNullable(f == null ? null : deepCopy(f));
	}

	public Optional<String> findSceneIdByFrameId(String frameId) {
		for (Map.Entry<String, List<FrameDto>> e : framesBySceneId.entrySet()) {
			for (FrameDto f : e.getValue()) {
				if (frameId.equals(f.getId())) {
					return Optional.of(e.getKey());
				}
			}
		}
		return Optional.empty();
	}

	public Optional<String> findScriptIdBySceneId(String sceneId) {
		String direct = sceneIdToScriptId.get(sceneId);
		if (direct != null) {
			return Optional.of(direct);
		}
		for (Map.Entry<String, ScriptRecord> e : scriptsById.entrySet()) {
			if (e.getValue().sceneIds.contains(sceneId)) {
				return Optional.of(e.getKey());
			}
		}
		return Optional.empty();
	}

	private SceneDto createSceneInternal(String scriptId, SceneDto src) {
		String id = generateSceneId();
		SceneDto scene = new SceneDto();
		scene.setId(id);
		scene.setTitle(src.getTitle());
		scene.setLocation(src.getLocation());
		scene.setCharacters(nullSafeList(src.getCharacters()));
		scene.setProps(nullSafeList(src.getProps()));
		scene.setDescription(src.getDescription());
		scene.setCurrentFrame(null);
		scene.setGeneratedFrames(new ArrayList<>());

		scenesById.put(id, scene);
		framesBySceneId.put(id, new ArrayList<>());
		sceneIdToScriptId.put(id, scriptId);
		return scene;
	}

	private ScriptRecord requireScript(String scriptId) {
		ScriptRecord s = scriptsById.get(scriptId);
		if (s == null) {
			throw new IllegalArgumentException("Script not found: " + scriptId);
		}
		return s;
	}

	private SceneDto requireScene(String sceneId) {
		SceneDto s = scenesById.get(sceneId);
		if (s == null) {
			throw new IllegalArgumentException("Scene not found: " + sceneId);
		}
		return s;
	}

	private static List<String> nullSafeList(List<String> list) {
		return list == null ? new ArrayList<>() : new ArrayList<>(list);
	}

	private static String generateScriptId() {
		return "script-" + UUID.randomUUID();
	}

	private static String generateSceneId() {
		return "scene-" + UUID.randomUUID();
	}

	private static String generateFrameId() {
		return "frame-" + UUID.randomUUID();
	}

	private static FrameDto deepCopy(FrameDto src) {
		if (src == null) return null;
		FrameDto c = new FrameDto();
		c.setId(src.getId());
		c.setImageUrl(src.getImageUrl());
		c.setDetailLevel(src.getDetailLevel());
		c.setPrompt(src.getPrompt());
		c.setSeed(src.getSeed());
		c.setCreatedAt(src.getCreatedAt());
		return c;
	}

	private static SceneDto deepCopy(SceneDto src) {
		if (src == null) return null;
		SceneDto c = new SceneDto();
		c.setId(src.getId());
		c.setTitle(src.getTitle());
		c.setLocation(src.getLocation());
		c.setCharacters(nullSafeList(src.getCharacters()));
		c.setProps(nullSafeList(src.getProps()));
		c.setDescription(src.getDescription());
		c.setCurrentFrame(deepCopy(src.getCurrentFrame()));
		List<FrameDto> frames = new ArrayList<>();
		if (src.getGeneratedFrames() != null) {
			for (FrameDto f : src.getGeneratedFrames()) {
				frames.add(deepCopy(f));
			}
		}
		c.setGeneratedFrames(frames);
		return c;
	}

	public static class ScriptRecord {
		public String id;
		public String filename;
		public String status;
		public List<String> sceneIds;

		@Override
		public String toString() {
			return "ScriptRecord{" +
					"id='" + id + '\'' +
					", filename='" + filename + '\'' +
					", status='" + status + '\'' +
					", sceneIds=" + Objects.toString(sceneIds) +
					'}';
		}
	}
}


