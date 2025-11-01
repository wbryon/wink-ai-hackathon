package ru.wink.winkaipreviz.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import ru.wink.winkaipreviz.config.StorageProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Set;

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

		Path dir = Path.of(props.getUploadDir());
		Files.createDirectories(dir);

		String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
		String targetName = Instant.now().toEpochMilli() + "_" + originalName;
		Path target = dir.resolve(targetName);

		Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
		return target.toString();
	}
}


