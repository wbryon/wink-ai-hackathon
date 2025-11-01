package ru.wink.winkaipreviz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageProperties {

	@Value("${app.storage.upload-dir:/data/uploads}")
	private String uploadDir;

	public String getUploadDir() {
		return uploadDir;
	}
}


