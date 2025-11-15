package ru.wink.winkaipreviz.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class TextExtractionService {

	private final Tika tika = new Tika();

	public String extractText(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) return "";
		try {
			// Let Tika auto-detect parser by content
			String text = tika.parseToString(file.getInputStream());
			return text == null ? "" : text.trim();
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			// Wrap non-IO parsing issues in IOException to be handled uniformly
			throw new IOException("Не удалось извлечь текст из файла", e);
		}
	}
}


