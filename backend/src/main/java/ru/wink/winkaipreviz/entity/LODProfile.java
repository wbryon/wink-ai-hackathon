package ru.wink.winkaipreviz.entity;

import java.util.List;

/**
 * Профиль уровня детализации (LOD) с параметрами генерации.
 * Определяет параметры для Sketch, Mid, Final и Direct Final.
 */
public enum LODProfile {
    
    /**
     * Эскиз (ч/б, композиция и читаемость).
     * Быстрый набросок для согласования композиции.
     */
    SKETCH(
            "sketch",
            8, 15,           // steps: 8-15
            5.0, 7.0,        // CFG: умеренный/пониженный
            null, null,      // denoise: не используется (text2img)
            "720",           // разрешение по большей стороне (длинная сторона 720 px)
            List.of("colors", "fine textures", "typography", "text watermark"), // негативы
            false,           // требует refiner
            false            // требует upscale
    ),
    
    /**
     * Средняя детализация (цвет, аккуратные материалы).
     * Добавляет цвет и базовую фактуру, сохраняя композицию.
     */
    MID(
            "mid",
            18, 28,          // steps: 18-28
            6.0, 8.0,        // CFG: умеренный
            0.25, 0.45,      // denoise: бережный диапазон для img2img
            "720",           // разрешение по большей стороне (длинная сторона 720 px)
            List.of("ultra-detailed skin pores", "complex patterns", "watermark", "low-res"), // негативы
            false,           // refiner опционален
            false            // upscale опционален
    ),
    
    /**
     * Финальный кадр (деталь, свет, чистые текстуры).
     * Доводит картинку до презентабельного качества.
     */
    FINAL(
            "final",
            22, 36,          // steps: 22-36
            7.0, 9.0,        // CFG: умеренно-высокий
            0.35, 0.55,      // denoise: чуть смелее для img2img
            "720",           // разрешение по большей стороне (длинная сторона 720 px)
            List.of("low-res", "extra fingers", "text", "artifact", "over-sharpen", "blurry"), // негативы
            true,            // рекомендуется refiner
            true             // рекомендуется upscale
    ),
    
    /**
     * Сразу финал (без этапа эскиза).
     * Финальный кадр с усиленной композицией и required-объектами.
     */
    DIRECT_FINAL(
            "direct_final",
            28, 40,          // steps: 28-40
            7.5, 9.5,        // CFG: умеренно-высокий
            null, null,      // denoise: не используется (text2img)
            "720",           // разрешение по большей стороне (длинная сторона 720 px)
            List.of("low-res", "extra fingers", "text", "artifact", "complex patterns", "watermark"), // негативы
            true,            // рекомендуется refiner
            true             // рекомендуется upscale
    );
    
    private final String code;
    private final int stepsMin;
    private final int stepsMax;
    private final double cfgMin;
    private final double cfgMax;
    private final Double denoiseMin;
    private final Double denoiseMax;
    private final String defaultResolution;
    private final List<String> defaultNegatives;
    private final boolean refinerRecommended;
    private final boolean upscaleRecommended;
    
    LODProfile(String code, int stepsMin, int stepsMax, double cfgMin, double cfgMax,
               Double denoiseMin, Double denoiseMax, String defaultResolution,
               List<String> defaultNegatives, boolean refinerRecommended, boolean upscaleRecommended) {
        this.code = code;
        this.stepsMin = stepsMin;
        this.stepsMax = stepsMax;
        this.cfgMin = cfgMin;
        this.cfgMax = cfgMax;
        this.denoiseMin = denoiseMin;
        this.denoiseMax = denoiseMax;
        this.defaultResolution = defaultResolution;
        this.defaultNegatives = defaultNegatives;
        this.refinerRecommended = refinerRecommended;
        this.upscaleRecommended = upscaleRecommended;
    }
    
    /**
     * Возвращает код профиля (для API и БД).
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Возвращает минимальное количество шагов.
     */
    public int getStepsMin() {
        return stepsMin;
    }
    
    /**
     * Возвращает максимальное количество шагов.
     */
    public int getStepsMax() {
        return stepsMax;
    }
    
    /**
     * Возвращает рекомендуемое количество шагов (середина диапазона).
     */
    public int getStepsRecommended() {
        return (stepsMin + stepsMax) / 2;
    }
    
    /**
     * Возвращает минимальный CFG.
     */
    public double getCfgMin() {
        return cfgMin;
    }
    
    /**
     * Возвращает максимальный CFG.
     */
    public double getCfgMax() {
        return cfgMax;
    }
    
    /**
     * Возвращает рекомендуемый CFG (середина диапазона).
     */
    public double getCfgRecommended() {
        return (cfgMin + cfgMax) / 2.0;
    }
    
    /**
     * Возвращает минимальный denoise для img2img (null если не используется).
     */
    public Double getDenoiseMin() {
        return denoiseMin;
    }
    
    /**
     * Возвращает максимальный denoise для img2img (null если не используется).
     */
    public Double getDenoiseMax() {
        return denoiseMax;
    }
    
    /**
     * Возвращает рекомендуемый denoise для img2img (середина диапазона, null если не используется).
     */
    public Double getDenoiseRecommended() {
        if (denoiseMin == null || denoiseMax == null) {
            return null;
        }
        return (denoiseMin + denoiseMax) / 2.0;
    }
    
    /**
     * Возвращает разрешение по умолчанию (по большей стороне).
     */
    public String getDefaultResolution() {
        return defaultResolution;
    }
    
    /**
     * Возвращает список негативных подсказок по умолчанию.
     */
    public List<String> getDefaultNegatives() {
        return defaultNegatives;
    }
    
    /**
     * Возвращает строку негативных подсказок, разделенных запятыми.
     */
    public String getDefaultNegativesString() {
        return String.join(", ", defaultNegatives);
    }
    
    /**
     * Рекомендуется ли использование refiner для этого профиля.
     */
    public boolean isRefinerRecommended() {
        return refinerRecommended;
    }
    
    /**
     * Рекомендуется ли использование upscale для этого профиля.
     */
    public boolean isUpscaleRecommended() {
        return upscaleRecommended;
    }
    
    /**
     * Проверяет, используется ли img2img для этого профиля (есть denoise).
     */
    public boolean isImg2Img() {
        return denoiseMin != null && denoiseMax != null;
    }
    
    /**
     * Преобразует DetailLevel в LODProfile.
     */
    public static LODProfile fromDetailLevel(DetailLevel level) {
        if (level == null) {
            return SKETCH;
        }
        return switch (level) {
            case SKETCH -> SKETCH;
            case MID -> MID;
            case FINAL -> FINAL;
            case DIRECT_FINAL -> DIRECT_FINAL;
        };
    }
    
    /**
     * Преобразует строку в LODProfile (case-insensitive).
     */
    public static LODProfile fromString(String value) {
        if (value == null || value.isBlank()) {
            return SKETCH;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "sketch" -> SKETCH;
            case "mid", "medium" -> MID;
            case "final" -> FINAL;
            case "direct_final", "direct-final", "directfinal" -> DIRECT_FINAL;
            default -> throw new IllegalArgumentException("Unknown LOD profile: " + value);
        };
    }
}

