package ru.wink.winkaipreviz.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.service.OllamaScriptParserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/script")
public class OllamaScriptController {

    private final OllamaScriptParserService scriptService;

    public OllamaScriptController(OllamaScriptParserService scriptService) {
        this.scriptService = scriptService;
    }

	/**
	 * Вариант 1: принимаем чанк как text/plain (raw текст).
	 */
	@PostMapping(
			path = "/parse",
			consumes = MediaType.TEXT_PLAIN_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public Map<String, Object> parseScriptPlain(@RequestBody String scriptChunk) throws Exception {
		List<Scene> scenes = scriptService.parseChunk(scriptChunk);
		return Map.of("results", scenes);
	}

	/**
	 * Вариант 2: принимаем чанк как JSON: { "script": "..." }.
	 */
	@PostMapping(
			path = "/parse",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public Map<String, Object> parseScriptJson(@RequestBody Map<String, String> body) throws Exception {
		String scriptChunk = body.get("script");
		List<Scene> scenes = scriptService.parseChunk(scriptChunk);
		return Map.of("results", scenes);
	}
}
