package ru.wink.winkaipreviz.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Запрос на генерацию кадра.
 * Поддерживает LOD профили и путь генерации (progressive/direct).
 */
public class GenerateFrameRequest {

    /**
     * Уровень детализации: sketch, mid, final, direct_final
     */
    @Pattern(regexp = "(?i)sketch|mid|final|direct_final|direct-final|directfinal", 
             message = "detailLevel должен быть одним из: sketch, mid, final, direct_final")
    private String detailLevel = "sketch";

    /**
     * Путь генерации: progressive (через эскиз) или direct (сразу финал).
     * Если не указан, определяется автоматически на основе detailLevel.
     */
    @Pattern(regexp = "(?i)progressive|direct", 
             message = "path должен быть одним из: progressive, direct")
    private String path;

    /**
     * Пользовательский промпт (опционально).
     * Если не указан, используется промпт из enriched JSON сцены.
     */
    @Size(max = 4000, message = "prompt слишком длинный (макс. 4000 символов)")
    private String prompt;

    /**
     * Seed для воспроизводимости (опционально).
     * Если не указан, генерируется случайно.
     */
    private Integer seed;

    /**
     * Модель для генерации (опционально).
     * Если не указана, используется модель по умолчанию из конфигурации.
     */
    @Size(max = 64, message = "model слишком длинное (макс. 64 символа)")
    private String model;

    // --- getters/setters ---
    public String getDetailLevel() { 
        return detailLevel; 
    }
    
    public void setDetailLevel(String detailLevel) { 
        this.detailLevel = detailLevel; 
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPrompt() { 
        return prompt; 
    }
    
    public void setPrompt(String prompt) { 
        this.prompt = prompt; 
    }

    public Integer getSeed() { 
        return seed; 
    }
    
    public void setSeed(Integer seed) { 
        this.seed = seed; 
    }

    public String getModel() { 
        return model; 
    }
    
    public void setModel(String model) { 
        this.model = model; 
    }
}
