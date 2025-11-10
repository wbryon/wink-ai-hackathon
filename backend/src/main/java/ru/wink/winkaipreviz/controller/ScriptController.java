package ru.wink.winkaipreviz.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.wink.winkaipreviz.dto.AddSceneRequest;
import ru.wink.winkaipreviz.dto.SceneDto;
import ru.wink.winkaipreviz.dto.ScriptUploadResponse;
import ru.wink.winkaipreviz.service.PrevizService;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final PrevizService service;

    public ScriptController(PrevizService service) {
        this.service = service;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScriptUploadResponse upload(@RequestParam("file") MultipartFile file) throws Exception {
        return service.createScriptFromUpload(file);
    }

    @GetMapping("/{scriptId}/scenes")
    public List<SceneDto> getScenes(@PathVariable String scriptId) {
        return service.getScenes(scriptId);
    }

    // addScene удалён в новой схеме; сцены создаются пайплайном парсинга

    @GetMapping("/{scriptId}/export")
    public ResponseEntity<Resource> export(@PathVariable String scriptId) {
        byte[] pdfStub = "%PDF-1.4\n% Stub Storyboard PDF for script: "
                .concat(scriptId)
                .getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(pdfStub);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=storyboard-" + scriptId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfStub.length)
                .body(resource);
    }
}


