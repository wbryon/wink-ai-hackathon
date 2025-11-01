package ru.wink.winkaipreviz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wink.winkaipreviz.entity.Scene;

import java.util.List;
import java.util.UUID;

public interface SceneRepository extends JpaRepository<Scene, UUID> {
	List<Scene> findByScript_Id(UUID scriptId);
}
