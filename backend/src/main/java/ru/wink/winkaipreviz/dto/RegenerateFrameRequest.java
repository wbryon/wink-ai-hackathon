package ru.wink.winkaipreviz.dto;

public class RegenerateFrameRequest {
	private String prompt;
	private String detailLevel; // sketch | medium | final

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public String getDetailLevel() {
		return detailLevel;
	}

	public void setDetailLevel(String detailLevel) {
		this.detailLevel = detailLevel;
	}
}


