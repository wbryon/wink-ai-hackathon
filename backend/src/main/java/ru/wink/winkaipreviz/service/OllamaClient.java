package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;

@Service
public class OllamaClient {

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Value("#{'${ollama.urls:http://localhost:11434/api/generate,http://localhost:11435/api/generate,http://localhost:11436/api/generate}'.split(',')}")
	private List<String> ollamaUrls;

	@Value("${ollama.model:mistral}")
	private String modelName;

	public Callable<String> buildTask(String sceneText, int index) {
		return () -> {
			String escaped = sceneText.replace("\"", "\\\"");
			String body = """
				{
				  "model": "%s",
				  "prompt": "Разбей следующую сцену на смысловые элементы и верни JSON:\\n%s"
				}
				""".formatted(modelName, escaped);

			String url = ollamaUrls.get(index % ollamaUrls.size());
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return response.body();
		};
	}
}


