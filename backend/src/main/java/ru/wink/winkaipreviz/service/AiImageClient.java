package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.wink.winkaipreviz.ai.ImageGenPort;
import ru.wink.winkaipreviz.ai.ImageResult;
import ru.wink.winkaipreviz.entity.DetailLevel;

import java.time.Instant;
import java.util.Map;

@Component
public class AiImageClient implements ImageGenPort {

    private final RestTemplate rest = new RestTemplate();

    @Value("${ai.generate.url:http://ai:8000/generate}")
    private String generateUrl;

    public ImageResult generate(String prompt, DetailLevel level) {
        Map<String, Object> body = Map.of(
                "prompt", prompt,
                "lod", level == null ? "mid" : level.name().toLowerCase()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> resp = rest.postForEntity(generateUrl, new HttpEntity<>(body, headers), (Class<Map<String, Object>>)(Class<?>)Map.class);
        Map<String, Object> v = resp.getBody();
        if (v == null) {
            return new ImageResult(null, "unknown", null, Instant.now());
        }
        String imageUrl = v.get("image_url") == null ? null : String.valueOf(v.get("image_url"));
        String model = v.get("model") == null ? "unknown" : String.valueOf(v.get("model"));
        Integer seed = null;
        Object seedObj = v.get("seed");
        if (seedObj instanceof Number n) seed = n.intValue();
        return new ImageResult(imageUrl, model, seed, Instant.now());
    }
}
