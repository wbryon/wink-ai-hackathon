package ru.wink.winkaipreviz.dto;

import java.util.List;

public class SceneDto {
	private String id;
	private String title;
	private String location;
	private List<String> characters;
	private List<String> props;
	private String description;
	private String prompt;
	private FrameDto currentFrame;
	private List<FrameDto> generatedFrames;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public List<String> getCharacters() {
		return characters;
	}

	public void setCharacters(List<String> characters) {
		this.characters = characters;
	}

	public List<String> getProps() {
		return props;
	}

	public void setProps(List<String> props) {
		this.props = props;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public FrameDto getCurrentFrame() {
		return currentFrame;
	}

	public void setCurrentFrame(FrameDto currentFrame) {
		this.currentFrame = currentFrame;
	}

	public List<FrameDto> getGeneratedFrames() {
		return generatedFrames;
	}

	public void setGeneratedFrames(List<FrameDto> generatedFrames) {
		this.generatedFrames = generatedFrames;
	}
}


