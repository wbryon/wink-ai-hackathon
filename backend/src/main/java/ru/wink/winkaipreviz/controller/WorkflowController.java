package ru.wink.winkaipreviz.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.wink.winkaipreviz.service.WorkflowService;

import java.util.Map;

/**
 * Контроллер для управления ComfyUI workflow файлами.
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * Загрузка workflow файла на сервер.
     * 
     * @param file JSON файл workflow
     * @param type тип workflow: "text2img" или "img2img"
     * @return результат загрузки
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadWorkflow(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type
    ) {
        try {
            String savedPath = workflowService.saveWorkflow(file, type);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", String.format("Workflow %s успешно сохранен", type),
                    "path", savedPath,
                    "type", type
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Ошибка при сохранении workflow: " + e.getMessage()
            ));
        }
    }

    /**
     * Получение информации о существующих workflow файлах.
     * 
     * @return информация о workflow файлах
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getWorkflowsInfo() {
        try {
            Map<String, Object> info = workflowService.getWorkflowsInfo();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", info
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Ошибка при получении информации: " + e.getMessage()
            ));
        }
    }

    /**
     * Health check endpoint для workflow сервиса.
     * 
     * @return статус сервиса
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "workflow-manager"
        ));
    }
}

