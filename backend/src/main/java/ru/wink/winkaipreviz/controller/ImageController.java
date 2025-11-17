package ru.wink.winkaipreviz.controller;

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

    @Value("${frames.storage-dir:/data/frames}")
    private String framesDir;

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String filename) throws MalformedURLException {
        Path path = Paths.get(framesDir).resolve(filename).normalize();

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }
}


