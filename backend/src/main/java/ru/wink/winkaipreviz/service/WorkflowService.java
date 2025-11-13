package ru.wink.winkaipreviz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/**
 * Сервис для управления ComfyUI workflow файлами.
 * Сохраняет workflow файлы в директорию ai-modules/workflows/
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("text2img", "img2img");
    private static final Set<String> REQUIRED_NODES_TEXT2IMG = Set.of(
            "CheckpointLoaderSimple", "KSampler", "CLIPTextEncode", 
            "EmptyLatentImage", "VAEDecode", "SaveImage"
    );
    private static final Set<String> REQUIRED_NODES_IMG2IMG = Set.of(
            "CheckpointLoaderSimple", "KSampler", "CLIPTextEncode",
            "LoadImage", "VAEEncode", "VAEDecode", "SaveImage"
    );

    private final ObjectMapper objectMapper;
    private final String workflowsDir;

    public WorkflowService(
            ObjectMapper objectMapper,
            @Value("${app.workflows.dir:../ai-modules/workflows}") String workflowsDir
    ) {
        this.objectMapper = objectMapper;
        // Нормализуем путь относительно корня проекта или абсолютный путь
        Path workflowsPath = Paths.get(workflowsDir);
        if (!workflowsPath.isAbsolute()) {
            // Если относительный путь, пытаемся найти относительно backend директории
            String backendDir = System.getProperty("user.dir");
            if (backendDir != null && backendDir.contains("backend")) {
                workflowsPath = Paths.get(backendDir).getParent().resolve(workflowsDir).normalize();
            } else {
                workflowsPath = Paths.get(workflowsDir).toAbsolutePath().normalize();
            }
        }
        this.workflowsDir = workflowsPath.toString();
        log.info("WorkflowService initialized with workflows directory: {}", this.workflowsDir);
    }

    /**
     * Сохраняет workflow файл на сервер.
     * 
     * @param file файл workflow (JSON)
     * @param type тип workflow: "text2img" или "img2img"
     * @return путь к сохраненному файлу
     * @throws IOException если не удалось сохранить файл
     * @throws IllegalArgumentException если файл невалиден
     */
    public String saveWorkflow(MultipartFile file, String type) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не передан или пустой");
        }

        if (type == null || !ALLOWED_TYPES.contains(type.toLowerCase())) {
            throw new IllegalArgumentException(
                    String.format("Недопустимый тип workflow: %s. Разрешены: %s", type, ALLOWED_TYPES)
            );
        }

        // Проверка размера файла (максимум 1MB для workflow)
        if (file.getSize() > 1024 * 1024) {
            throw new IllegalArgumentException("Размер файла превышает 1MB");
        }

        // Читаем содержимое файла
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);

        // Валидация JSON
        JsonNode workflowJson;
        try {
            workflowJson = objectMapper.readTree(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("Файл не является валидным JSON: " + e.getMessage());
        }

        // Валидация структуры workflow
        validateWorkflowStructure(workflowJson, type);

        // Определяем путь для сохранения
        Path workflowsPath = Paths.get(this.workflowsDir);
        Files.createDirectories(workflowsPath);

        String filename = type.toLowerCase() + ".json";
        Path targetFile = workflowsPath.resolve(filename);

        // Сохраняем файл
        Files.writeString(targetFile, content, StandardCharsets.UTF_8);
        log.info("Workflow сохранен: type={}, path={}", type, targetFile);

        return targetFile.toString();
    }

    /**
     * Валидирует структуру workflow JSON.
     * 
     * @param workflowJson JSON workflow
     * @param type тип workflow
     * @throws IllegalArgumentException если структура невалидна
     */
    private void validateWorkflowStructure(JsonNode workflowJson, String type) {
        if (!workflowJson.isObject()) {
            throw new IllegalArgumentException("Workflow должен быть объектом");
        }

        // Собираем все типы нод из workflow
        java.util.Set<String> actualNodeTypes = new java.util.HashSet<>();
        workflowJson.fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            if (node.isObject() && node.has("class_type")) {
                actualNodeTypes.add(node.get("class_type").asText());
            }
        });

        // Проверяем обязательные ноды в зависимости от типа
        Set<String> requiredNodes = type.equalsIgnoreCase("text2img") 
                ? REQUIRED_NODES_TEXT2IMG 
                : REQUIRED_NODES_IMG2IMG;

        java.util.Set<String> missingNodes = new java.util.HashSet<>(requiredNodes);
        missingNodes.removeAll(actualNodeTypes);

        if (!missingNodes.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Отсутствуют обязательные ноды для %s: %s", type, missingNodes)
            );
        }

        // Проверяем наличие KSampler
        if (!actualNodeTypes.contains("KSampler")) {
            throw new IllegalArgumentException("Workflow должен содержать ноду KSampler");
        }
    }

    /**
     * Получает информацию о существующих workflow файлах.
     * 
     * @return Map с информацией о workflow файлах
     */
    public Map<String, Object> getWorkflowsInfo() {
        Path workflowsPath = Paths.get(this.workflowsDir);
        
        boolean text2imgExists = Files.exists(workflowsPath.resolve("text2img.json"));
        boolean img2imgExists = Files.exists(workflowsPath.resolve("img2img.json"));

        return Map.of(
                "workflows_dir", workflowsPath.toString(),
                "text2img", Map.of("exists", text2imgExists),
                "img2img", Map.of("exists", img2imgExists)
        );
    }
}

