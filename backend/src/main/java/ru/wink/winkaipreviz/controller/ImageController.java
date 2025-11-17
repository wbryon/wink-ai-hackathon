package ru.wink.winkaipreviz.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Контроллер для раздачи сгенерированных кадров.
 * Используется совместно с ai‑сервисом, который сохраняет изображения в общую директорию.
 *
 * URL вида /api/images/{filename}.png должен совпадать с IMAGE_URL_BASE в ai‑modules.
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    @Value("${frames.storage-dir:/data/frames}")
    private String framesDir;

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) throws MalformedURLException {
        Path path = Paths.get(framesDir).resolve(filename).normalize();
        boolean exists = Files.exists(path) && Files.isRegularFile(path);

        log.info("Request image: filename='{}', resolvedPath='{}', exists={}", filename, path, exists);

        if (!exists) {
            log.warn("Image not found for filename='{}', resolvedPath='{}'", filename, path);
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }
}



