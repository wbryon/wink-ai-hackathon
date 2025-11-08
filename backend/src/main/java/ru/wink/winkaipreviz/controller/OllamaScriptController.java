package ru.wink.winkaipreviz.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

	@PostMapping(path = "/parse", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> parseScript(@RequestBody Map<String, String> body) throws InterruptedException {
		String script = body.get("script");
		List<String> results = scriptService.processScript(script);
		return Map.of("results", results);
	}
}


