package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.wink.winkaipreviz.ai.ImageGenPort;
import ru.wink.winkaipreviz.ai.ImageResult;
import ru.wink.winkaipreviz.entity.DetailLevel;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class AiImageClient implements ImageGenPort {

    private final RestTemplate rest = new RestTemplate();

    @Value("${ai.generate.url:http://ai:8000/generate}")
    private String generateUrl;

    public ImageResult generate(String prompt, DetailLevel level) {
        Map<String, Object> body = Map.of(
                "prompt", prompt,
                "lod", Optional.ofNullable(level).map(Enum::name).orElse("MID").toLowerCase()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map<String, Object>> resp = rest.exchange(
                    generateUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {}
            );
            Map<String, Object> v = resp.getBody();
            if (v == null) {
                return new ImageResult(null, "unknown", null, Instant.now());
            }

            String imageUrl = Optional.ofNullable(v.get("image_url")).map(Object::toString).orElse(null);
            String model = Optional.ofNullable(v.get("model")).map(Object::toString).orElse("unknown");
            Integer seed = v.get("seed") instanceof Number n ? n.intValue() : null;

            return new ImageResult(imageUrl, model, seed, Instant.now());
        } catch (RestClientException ex) {
            return new ImageResult(null, "error", null, Instant.now());
        }
    }
}
