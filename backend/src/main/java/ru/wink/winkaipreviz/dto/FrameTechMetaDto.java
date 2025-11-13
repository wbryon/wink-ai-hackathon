package ru.wink.winkaipreviz.dto;

import java.util.List;

/**
 * Технические метаданные кадра (frame meta / frame-card).
 * Сериализуются в JSON и хранятся в Frame.enrichedJson.
 * Соответствует структуре из документации (frame-card).
 */
public record FrameTechMetaDto(
        /**
         * Seed для воспроизводимости.
         */
        Long seed,
        
        /**
         * Количество шагов генерации.
         */
        Integer steps,
        
        /**
         * CFG (guidance scale) - степень следования промпту.
         */
        Double cfg,
        
        /**
         * Sampler (метод выборки): euler_a, dpmpp_2m, и т.д.
         */
        String sampler,
        
        /**
         * Scheduler (планировщик шума): default, karras, и т.д.
         */
        String scheduler,
        
        /**
         * Разрешение кадра (например, "1536x896").
         */
        String resolution,
        
        /**
         * VAE (Variational Autoencoder) - влияет на палитру и контраст.
         */
        String vae,
        
        /**
         * Control-модули (pose/edge/depth) с параметрами.
         */
        List<ControlMetaDto> controls,
        
        /**
         * Параметры img2img (denoise strength).
         */
        Img2ImgMetaDto img2img,
        
        /**
         * Стилистические настройки (preset, negatives).
         */
        StyleMetaDto style,
        
        /**
         * Настройки refiner-этапа.
         */
        RefinerMetaDto refiner,
        
        /**
         * Параметры апскейла.
         */
        UpscaleMetaDto upscale,
        
        /**
         * LOD профиль, использованный для генерации.
         */
        String lod,
        
        /**
         * Путь генерации: progressive или direct.
         */
        String path,
        
        /**
         * Время в очереди (миллисекунды).
         */
        Long queueMs,
        
        /**
         * Время выполнения генерации (миллисекунды).
         */
        Long runMs,
        
        /**
         * Использованная VRAM (GB).
         */
        Integer vramGb
) {
    /**
     * Создает FrameTechMetaDto с минимальным набором полей.
     */
    public FrameTechMetaDto(Long seed, Integer steps, Double cfg, String sampler, 
                           String scheduler, String resolution, String vae,
                           List<ControlMetaDto> controls, Img2ImgMetaDto img2img,
                           StyleMetaDto style, RefinerMetaDto refiner, UpscaleMetaDto upscale) {
        this(seed, steps, cfg, sampler, scheduler, resolution, vae, controls, img2img, 
             style, refiner, upscale, null, null, null, null, null);
    }
}


