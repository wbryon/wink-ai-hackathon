package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.wink.winkaipreviz.entity.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AiParserClient {

	private final RestTemplate restTemplate = new RestTemplate();

	@Value("${ai.parser.url:http://ai:8000/parse}")
	private String aiParserUrl;

	@SuppressWarnings("unchecked")
	public List<Scene> parse(String fullText) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			Map<String, Object> body = Map.of("text", fullText);
			HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
			Map<String, Object> response = restTemplate.postForObject(aiParserUrl, entity, Map.class);
			if (response == null) return List.of();
			Object scenesObj = response.get("scenes");
			if (!(scenesObj instanceof List<?> list)) return List.of();
			List<Scene> scenes = new ArrayList<>();
			for (Object o : list) {
				if (!(o instanceof Map<?, ?> m)) continue;
				Scene s = new Scene();
				s.setTitle(asString(m.get("title")));
				s.setLocation(asString(m.get("location")));
				s.setDescription(asString(m.get("description")));
				s.setCharacters(asStringList(m.get("characters")));
				s.setProps(asStringList(m.get("props")));
				scenes.add(s);
			}
			return scenes;
		} catch (RestClientException ex) {
			return List.of();
		}
	}

	private static String asString(Object v) {
		return v == null ? null : String.valueOf(v);
	}

	private static List<String> asStringList(Object v) {
		if (v instanceof List<?> l) {
			List<String> out = new ArrayList<>();
			for (Object it : l) out.add(asString(it));
			return out;
		}
		return new ArrayList<>();
	}
}


