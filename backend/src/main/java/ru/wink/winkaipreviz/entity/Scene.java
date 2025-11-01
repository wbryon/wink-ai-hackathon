package ru.wink.winkaipreviz.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scenes")
public class Scene {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "script_id", nullable = false)
	private Script script;

	@Column(name = "title")
	private String title;

	@Column(name = "location")
	private String location;

	@ElementCollection
	@CollectionTable(name = "scene_characters", joinColumns = @JoinColumn(name = "scene_id"))
	@Column(name = "character")
	private List<String> characters = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "scene_props", joinColumns = @JoinColumn(name = "scene_id"))
	@Column(name = "prop")
	private List<String> props = new ArrayList<>();

	@Column(name = "description", length = 4000)
	private String description;

	@Column(name = "prompt", length = 4000)
	private String prompt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "scene", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Frame> frames = new ArrayList<>();

	@PrePersist
	public void prePersist() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public Script getScript() {
		return script;
	}

	public void setScript(Script script) {
		this.script = script;
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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public List<Frame> getFrames() {
		return frames;
	}

	public void setFrames(List<Frame> frames) {
		this.frames = frames;
	}
}


