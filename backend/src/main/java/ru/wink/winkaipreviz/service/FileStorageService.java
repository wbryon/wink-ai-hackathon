package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import ru.wink.winkaipreviz.config.StorageProperties;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

	private static final Set<String> ALLOWED = Set.of(
			"application/pdf",
			"application/msword",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document"
	);

	private final StorageProperties props;

	public FileStorageService(StorageProperties props) {
		this.props = props;
	}

	public String store(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Файл не передан или пустой");
		}
		if (file.getSize() > 50L * 1024 * 1024) {
			throw new IllegalArgumentException("Размер файла превышает 50MB");
		}
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED.contains(contentType)) {
			throw new IllegalArgumentException("Поддерживаются только PDF/DOC/DOCX");
		}

		String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
		String targetName = Instant.now().toEpochMilli() + "_" + originalName;

		// Сохраняем файлы в постоянную директорию /data/uploads
		Path uploadDir = Path.of(props.getUploadDir());
		Files.createDirectories(uploadDir);
		Path target = uploadDir.resolve(targetName);

		Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
		return target.toString();
	}

	public String extractText(String filePath) throws IOException, TikaException {
		if (filePath == null) throw new IllegalArgumentException("filePath is null");
		Tika tika = new Tika();
		return tika.parseToString(new File(filePath));
	}

	/**
	 * Удаляет исходный загруженный файл сценария с диска.
	 * Используется после завершения фоновой обработки, чтобы DOCX/PDF не хранились на сервере.
	 */
	public void deleteUploadedFileQuietly(String filePath) {
		if (filePath == null || filePath.isBlank()) {
			return;
		}
		try {
			Files.deleteIfExists(Path.of(filePath));
		} catch (IOException ignored) {
			// намеренно игнорируем ошибки — отсутствие файла не критично
		}
	}

	public List<String> saveChunks(UUID scriptId, List<String> chunks) throws IOException {
		if (chunks == null) return List.of();
		Path dir = Path.of(props.getUploadDir(), "chunks", scriptId.toString());
		Files.createDirectories(dir);
		List<String> saved = new ArrayList<>();
		for (int i = 0; i < chunks.size(); i++) {
			String filename = String.format("chunk_%03d.txt", i + 1);
			Path target = dir.resolve(filename);
			String content = chunks.get(i) == null ? "" : chunks.get(i);
			Files.writeString(target, content, StandardCharsets.UTF_8);
			saved.add(target.toString());
		}
		return saved;
	}
}


