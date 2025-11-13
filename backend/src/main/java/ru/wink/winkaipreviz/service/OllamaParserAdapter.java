package ru.wink.winkaipreviz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.wink.winkaipreviz.ai.ParserPort;
import ru.wink.winkaipreviz.entity.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * Адаптер ParserPort, который использует OllamaScriptParserService
 * для парсинга текста (чанка) в список Scene.
 */
@Component
public class OllamaParserAdapter implements ParserPort {

	private static final Logger log = LoggerFactory.getLogger(OllamaParserAdapter.class);

	private final OllamaScriptParserService ollamaScriptParserService;

	// Храним последний сырой JSON для совместимости с ParserPort
	private String lastRawJson;

	public OllamaParserAdapter(OllamaScriptParserService ollamaScriptParserService) {
		this.ollamaScriptParserService = ollamaScriptParserService;
	}

	@Override
	public List<Scene> parseScenes(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		try {
			// OllamaScriptParserService.parseChunk уже делает полный пайплайн:
			// prompt -> /api/generate -> JSON-массив -> List<Scene>
			List<Scene> scenes = ollamaScriptParserService.parseChunk(text);

			// Для совместимости с getLastRawJson можно сохранить нормализованный JSON
			// как aggregated массив (если нужно дебажить).
			if (scenes != null && !scenes.isEmpty()) {
				List<String> jsonScenes = new ArrayList<>();
				for (Scene s : scenes) {
					if (s.getOriginalJson() != null) {
						jsonScenes.add(s.getOriginalJson());
					}
				}
				if (!jsonScenes.isEmpty()) {
					this.lastRawJson = "[" + String.join(",", jsonScenes) + "]";
				}
			}

			return scenes == null ? List.of() : scenes;
		} catch (Exception e) {
			log.error("OllamaParserAdapter.parseScenes failed: {}", e.getMessage(), e);
			return List.of();
		}
	}

	@Override
	public String getLastRawJson() {
		return lastRawJson;
	}
}


