package ru.wink.winkaipreviz.dto;

import java.util.List;

/**
 * Человекочитаемые слоты промпта для визуализации сцены.
 * Собираются из enriched JSON (см. SceneVisualSpec_v1) и используются на фронте
 * для удобного редактирования и объяснения кадра.
 * 
 * Расширенная версия с поддержкой всех слотов:
 * - КТО (characters с атрибутами)
 * - ГДЕ (location, INT/EXT, time)
 * - ЧТО (action, required props)
 * - КОМПОЗИЦИЯ (shot_size, camera_angle, locational_cues)
 * - ТОН (tone[])
 * - СТИЛЬ (style_hints[])
 * - Негативы (глобальные + сценовые)
 */
public record PromptSlotsDto(
        // КТО: персонажи с атрибутами
        List<CharacterSlotDto> characters,
        
        // ГДЕ: локация, INT/EXT, время суток
        LocationSlotDto location,
        
        // ЧТО: действие и обязательные реквизиты
        ActionSlotDto action,
        
        // КОМПОЗИЦИЯ: размер кадра, угол камеры, локационные подсказки
        CompositionSlotDto composition,
        
        // ТОН: эмоциональная окраска (массив)
        List<String> tone,
        
        // СТИЛЬ: стилистические подсказки (массив)
        List<String> styleHints,
        
        // Негативы: глобальные и сценовые негативные подсказки
        NegativePromptSlotDto negatives,
        
        // Освещение (legacy, для обратной совместимости)
        String lighting,
        
        // Технические подсказки (legacy, для обратной совместимости)
        String technical
) {
    /**
     * Слот персонажа с атрибутами.
     */
    public record CharacterSlotDto(
            String name,
            String appearance,      // внешность
            List<String> clothing,  // одежда
            String pose,            // поза
            String action,          // действие персонажа
            String positionInFrame, // позиция в кадре
            String emotion          // эмоция
    ) {}
    
    /**
     * Слот локации с INT/EXT и временем суток.
     */
    public record LocationSlotDto(
            String raw,             // исходная строка локации
            String normalized,      // нормализованная локация
            String description,     // описание локации
            List<String> environmentDetails, // детали окружения
            String sceneType,       // INT или EXT
            TimeSlotDto time        // время суток
    ) {}
    
    /**
     * Слот времени суток.
     */
    public record TimeSlotDto(
            String raw,             // исходная строка времени
            String normalized,      // нормализованное время
            String description      // визуальное описание времени суток
    ) {}
    
    /**
     * Слот действия и реквизита.
     */
    public record ActionSlotDto(
            String mainAction,      // основное действие в сцене
            List<PropSlotDto> props // реквизиты (с флагом обязательности)
    ) {}
    
    /**
     * Слот реквизита.
     */
    public record PropSlotDto(
            String name,
            boolean required,       // обязательный или нет
            String owner            // владелец (имя персонажа или null)
    ) {}
    
    /**
     * Слот композиции кадра.
     */
    public record CompositionSlotDto(
            String shotType,        // размер кадра (medium shot, wide shot, etc.)
            String cameraAngle,    // угол камеры (eye level, low angle, etc.)
            String framing,         // композиция кадра
            String motion,          // движение в кадре
            List<String> locationalCues // локационные подсказки ("у окна", "у стойки")
    ) {}
    
    /**
     * Слот негативных подсказок.
     */
    public record NegativePromptSlotDto(
            List<String> global,    // глобальные негативы (для всех сцен)
            List<String> sceneSpecific // специфичные для сцены негативы
    ) {}
}


