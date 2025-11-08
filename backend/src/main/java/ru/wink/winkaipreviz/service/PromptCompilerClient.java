package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.wink.winkaipreviz.entity.DetailLevel;
import ru.wink.winkaipreviz.entity.Scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PromptCompilerClient {

	private final RestTemplate restTemplate = new RestTemplate();

	@Value("${prompt.compiler.url:http://prompt-compiler:8010}")
	private String compilerBaseUrl;

	public String compile(Scene scene, DetailLevel level) {
		String url = compilerBaseUrl.endsWith("/") ? compilerBaseUrl + "compile" : compilerBaseUrl + "/compile";

		Map<String, Object> payload = new HashMap<>();
		payload.put("scene_id", scene.getId() == null ? "" : scene.getId().toString());
		payload.put("title", scene.getTitle());
		payload.put("location", scene.getLocation());
		payload.put("description", scene.getSemanticSummary() != null ? scene.getSemanticSummary() : scene.getDescription());
		payload.put("characters", scene.getCharacters() == null ? new ArrayList<>() : new ArrayList<>(scene.getCharacters()));
		payload.put("props", scene.getProps() == null ? new ArrayList<>() : new ArrayList<>(scene.getProps()));
		payload.put("tone", toList(scene.getTone()));
		payload.put("style", toList(scene.getStyle()));
		payload.put("lod", level == null ? "sketch" : level.name().toLowerCase());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		Map<?, ?> resp = restTemplate.postForObject(url, new HttpEntity<>(payload, headers), Map.class);
		if (resp == null) return "";
		Object p = resp.get("prompt");
		return p == null ? "" : String.valueOf(p);
	}

	private static List<String> toList(String value) {
		List<String> out = new ArrayList<>();
		if (value != null && !value.isBlank()) out.add(value);
		return out;
	}
}


