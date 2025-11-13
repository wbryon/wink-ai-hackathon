package ru.wink.winkaipreviz.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Короткая текстовая правка сцены от пользователя.
 * Например: "Сделать атмосферу более мрачной и добавить дождь за окном".
 */
public class RefineSceneRequest {

    @NotBlank
    private String instruction;

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }
}


