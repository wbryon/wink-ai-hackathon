package ru.wink.winkaipreviz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.wink.winkaipreviz.ai.ImageGenPort;
import ru.wink.winkaipreviz.ai.ImageResult;
import ru.wink.winkaipreviz.entity.DetailLevel;
import ru.wink.winkaipreviz.entity.LODProfile;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AiImageClient implements ImageGenPort {

    private static final Logger log = LoggerFactory.getLogger(AiImageClient.class);
    
    private final WebClient webClient;
    private final ExecutorService virtualExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiImageClient(@Value("${ai.generate.base-url:http://ai:8000}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "wink-previz-ai-client/1.0 (Java21)")
                .build();

        // 🟢 создаём пул из виртуальных потоков
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public ImageResult generate(String prompt, DetailLevel level) {
        try (var scope = virtualExecutor) {
            // выполняем синхронный вызов в лёгком виртуальном потоке
            return scope.submit(() -> doGenerate(prompt, level)).get();
        } catch (Exception e) {
            return new ImageResult(null, "error", null, Instant.now(), null);
        }
    }

    /**
     * Генерирует изображение с использованием LOD профиля и параметров.
     * Передает полные параметры генерации в AI модуль.
     */
    public ImageResult generateWithProfile(String prompt, LODProfile lodProfile, Integer seed, String model) {
        try (var scope = virtualExecutor) {
            return scope.submit(() -> doGenerateWithProfile(prompt, lodProfile, seed, model, null, null)).get();
        } catch (Exception e) {
            return new ImageResult(null, "error", null, Instant.now(), null);
        }
    }

    /**
     * Генерирует изображение через img2img (image-to-image) с использованием родительского изображения.
     * Используется для progressive path: Sketch → Mid → Final.
     * 
     * @param prompt промпт для генерации
     * @param lodProfile LOD профиль целевого уровня детализации
     * @param parentImageUrl URL родительского изображения (например, Sketch для Mid)
     * @param denoiseStrength сила деноизинга (0.0-1.0), определяет насколько сильно изменять изображение
     * @param seed seed для воспроизводимости (опционально)
     * @param model модель для генерации (опционально)
     * @return результат генерации
     */
    public ImageResult generateImg2Img(String prompt, LODProfile lodProfile, String parentImageUrl, 
                                      Double denoiseStrength, Integer seed, String model) {
        try (var scope = virtualExecutor) {
            return scope.submit(() -> doGenerateWithProfile(prompt, lodProfile, seed, model, parentImageUrl, denoiseStrength)).get();
        } catch (Exception e) {
            return new ImageResult(null, "error", null, Instant.now(), null);
        }
    }

    private ImageResult doGenerate(String prompt, DetailLevel level) {
        Map<String, Object> body = Map.of(
                "prompt", prompt,
                "lod", Optional.ofNullable(level).map(Enum::name).orElse("MID").toLowerCase()
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/generate")
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            res -> res.createException().flatMap(Mono::error))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block(); // блокируем, но только виртуальный поток

            if (response == null) {
                return new ImageResult(null, "unknown", null, Instant.now(), null);
            }

            String imageUrl = Optional.ofNullable(response.get("image_url")).map(Object::toString).orElse(null);
            String responseModel = Optional.ofNullable(response.get("model")).map(Object::toString).orElse("unknown");
            Integer seed = response.get("seed") instanceof Number n ? n.intValue() : null;

            // Собираем metaJson: всё, что вернул сервис, кроме основных полей
            String metaJson = null;
            try {
                Map<String, Object> meta = new HashMap<>(response);
                meta.remove("image_url");
                meta.remove("model");
                meta.remove("seed");
                metaJson = meta.isEmpty() ? null : objectMapper.writeValueAsString(meta);
            } catch (Exception ignored) {
                // если сериализация метаданных не удалась — просто не пишем metaJson
            }

            return new ImageResult(imageUrl, responseModel, seed, Instant.now(), metaJson);
        } catch (WebClientResponseException e) {
            return new ImageResult(null, "http-" + e.getStatusCode().value(), null, Instant.now(), null);
        } catch (Exception e) {
            return new ImageResult(null, "error", null, Instant.now(), null);
        }
    }

    private ImageResult doGenerateWithProfile(String prompt, LODProfile lodProfile, Integer seed, 
                                              String modelOverride, String parentImageUrl, Double denoiseStrength) {
        // Определяем, используем ли мы img2img или text2img
        boolean isImg2Img = parentImageUrl != null && !parentImageUrl.isBlank();
        String endpoint = isImg2Img ? "/img2img" : "/generate";
        
        log.debug("Generating image: endpoint={}, lod={}, isImg2Img={}", endpoint, lodProfile.getCode(), isImg2Img);
        
        // Собираем тело запроса с параметрами из LOD профиля
        Map<String, Object> body = new HashMap<>();
        body.put("prompt", prompt);
        body.put("lod", lodProfile.getCode());
        
        // Negative prompt: объединяем список в строку
        String negativePrompt = String.join(", ", lodProfile.getDefaultNegatives());
        body.put("negative_prompt", negativePrompt);
        
        // Параметры генерации
        body.put("steps", lodProfile.getStepsRecommended());
        body.put("cfg", lodProfile.getCfgRecommended());
        
        // Resolution: парсим строку "1024" или "1024x768" в массив [width, height]
        String resolutionStr = lodProfile.getDefaultResolution();
        int[] resolution = parseResolution(resolutionStr);
        // Используем List для правильной сериализации в JSON массив
        body.put("resolution", java.util.Arrays.asList(resolution[0], resolution[1]));
        
        // Sampler (по умолчанию из профиля, если доступен)
        // В текущей реализации LOD профили не содержат sampler, используем значение по умолчанию
        // body.put("sampler", "euler_a"); // опционально
        
        if (seed != null) {
            body.put("seed", seed);
        }
        if (modelOverride != null && !modelOverride.isBlank()) {
            body.put("model", modelOverride);
        }
        
        // Для img2img добавляем параметры изображения и denoise
        if (isImg2Img) {
            body.put("image_url", parentImageUrl);
            double finalDenoise = denoiseStrength != null ? denoiseStrength : lodProfile.getDenoiseRecommended();
            body.put("denoise", finalDenoise);
            log.debug("Img2img params: image_url={}, denoise={}", parentImageUrl, finalDenoise);
        }
        
        log.debug("Request body: prompt length={}, steps={}, cfg={}, resolution={}x{}", 
                prompt.length(), lodProfile.getStepsRecommended(), lodProfile.getCfgRecommended(), 
                resolution[0], resolution[1]);

        try {
            Map<String, Object> response = webClient.post()
                    .uri(endpoint)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            res -> res.createException().flatMap(Mono::error))
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMinutes(5)) // увеличенный таймаут для генерации
                    .block();

            if (response == null) {
                return new ImageResult(null, "unknown", null, Instant.now(), null);
            }

            String imageUrl = Optional.ofNullable(response.get("image_url")).map(Object::toString).orElse(null);
            String responseModel = Optional.ofNullable(response.get("model")).map(Object::toString).orElse(modelOverride != null ? modelOverride : "unknown");
            Integer responseSeed = null;
            Object seedObj = response.get("seed");
            if (seedObj instanceof Number n) {
                responseSeed = n.intValue();
            } else if (seed != null) {
                responseSeed = seed;
            }

            // Собираем metaJson: всё, что вернул сервис, включая параметры генерации
            String metaJson = null;
            try {
                Map<String, Object> meta = new HashMap<>(response);
                meta.remove("image_url");
                meta.remove("model");
                meta.remove("seed");
                // Добавляем параметры из профиля, если их нет в ответе
                if (!meta.containsKey("steps")) {
                    meta.put("steps", lodProfile.getStepsRecommended());
                }
                if (!meta.containsKey("cfg")) {
                    meta.put("cfg", lodProfile.getCfgRecommended());
                }
                if (!meta.containsKey("resolution")) {
                    meta.put("resolution", resolutionStr);
                }
                if (isImg2Img && !meta.containsKey("denoise")) {
                    double finalDenoise = denoiseStrength != null ? denoiseStrength : lodProfile.getDenoiseRecommended();
                    meta.put("denoise", finalDenoise);
                }
                metaJson = objectMapper.writeValueAsString(meta);
            } catch (Exception e) {
                // если сериализация метаданных не удалась — просто не пишем metaJson
            }

            log.debug("Generation successful: image_url={}, model={}, seed={}", imageUrl, responseModel, responseSeed);
            return new ImageResult(imageUrl, responseModel, responseSeed, Instant.now(), metaJson);
        } catch (WebClientResponseException e) {
            // Логируем ошибку для отладки
            log.error("AI service HTTP error: {} - {} - Endpoint: {} - Request body: {}", 
                    e.getStatusCode(), e.getResponseBodyAsString(), endpoint, body, e);
            return new ImageResult(null, "http-" + e.getStatusCode().value(), null, Instant.now(), null);
        } catch (Exception e) {
            // Логируем исключение для отладки
            log.error("AI service exception during {}: {} - Request body: {}", 
                    endpoint, e.getMessage(), body, e);
            return new ImageResult(null, "error", null, Instant.now(), null);
        }
    }
    
    /**
     * Парсит строку разрешения в массив [width, height].
     * Поддерживает форматы: "1024", "1024x768", "1024x640".
     * По умолчанию возвращает [1024, 768].
     */
    private int[] parseResolution(String resolutionStr) {
        if (resolutionStr == null || resolutionStr.isBlank()) {
            return new int[]{1024, 768}; // значение по умолчанию
        }
        
        try {
            // Если это просто число (например, "1024"), используем его как ширину, высоту вычисляем пропорционально
            if (resolutionStr.matches("\\d+")) {
                int width = Integer.parseInt(resolutionStr);
                // Для квадратных или широких форматов используем стандартные пропорции
                int height = (int) (width * 0.75); // 4:3 пропорция
                return new int[]{width, height};
            }
            
            // Если формат "1024x768" или "1024x640"
            if (resolutionStr.contains("x") || resolutionStr.contains("X")) {
                String[] parts = resolutionStr.split("[xX]");
                if (parts.length == 2) {
                    int width = Integer.parseInt(parts[0].trim());
                    int height = Integer.parseInt(parts[1].trim());
                    return new int[]{width, height};
                }
            }
        } catch (NumberFormatException e) {
            // Если парсинг не удался, возвращаем значение по умолчанию
        }
        
        // Значение по умолчанию
        return new int[]{1024, 768};
    }
}