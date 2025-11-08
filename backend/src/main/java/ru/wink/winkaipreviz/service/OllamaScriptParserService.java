package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class OllamaScriptParserService {

	private final OllamaClient ollamaClient;

	public OllamaScriptParserService(OllamaClient ollamaClient) {
		this.ollamaClient = ollamaClient;
	}

	public List<String> processScript(String fullScript) throws InterruptedException {
		List<String> scenes = splitScript(fullScript);
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

		List<Callable<String>> tasks = new ArrayList<>();
		for (int i = 0; i < scenes.size(); i++) {
			tasks.add(ollamaClient.buildTask(scenes.get(i), i));
		}

		List<Future<String>> futures = executor.invokeAll(tasks);
		executor.shutdown();

		List<String> results = new ArrayList<>();
		for (Future<String> f : futures) {
			try {
				results.add(f.get());
			} catch (ExecutionException e) {
				results.add("{\"error\":\"" + e.getMessage() + "\"}");
			}
		}
		return results;
	}

	private List<String> splitScript(String fullScript) {
		if (fullScript == null || fullScript.isBlank()) return List.of();
		return List.of(fullScript.split("(?=INT\\.|EXT\\.)"));
	}
}


