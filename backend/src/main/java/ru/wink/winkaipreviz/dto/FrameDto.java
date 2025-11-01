package ru.wink.winkaipreviz.dto;

public class FrameDto {
	private String id;
	private String imageUrl;
	private String detailLevel; // sketch | medium | final
	private String prompt;
	private Integer seed;
	private String createdAt; // ISO-8601

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public String getDetailLevel() {
		return detailLevel;
	}

	public void setDetailLevel(String detailLevel) {
		this.detailLevel = detailLevel;
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

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}
}


