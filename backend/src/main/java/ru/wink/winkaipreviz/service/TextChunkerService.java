package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunkerService {

	@Value("${app.parser.max-chars-per-chunk:4000}")
	private int maxCharsPerChunk;

	@Value("${app.parser.max-chunks:100}")
	private int maxChunks;

	/**
	 * Простое чанкирование по абзацам с ограничением размера.
	 * Безопасно для моделей ~8k токенов (на уровне символов с запасом).
	 */
	public List<String> chunk(String text) {
		List<String> chunks = new ArrayList<>();
		if (text == null || text.isBlank()) return chunks;

		String[] paragraphs = text.split("\\R{2,}"); // по пустым строкам
		StringBuilder current = new StringBuilder();

		for (String p : paragraphs) {
			String para = p.strip();
			if (para.isEmpty()) continue;
			// +2 на разделитель абзацев
			if (current.length() + para.length() + 2 > maxCharsPerChunk) {
				if (current.length() > 0) {
					chunks.add(current.toString());
					if (chunks.size() >= maxChunks) return chunks;
					current.setLength(0);
				}
			}
			if (current.length() > 0) current.append("\n\n");
			current.append(para);
		}
		if (current.length() > 0 && chunks.size() < maxChunks) {
			chunks.add(current.toString());
		}
		return chunks;
	}
}


