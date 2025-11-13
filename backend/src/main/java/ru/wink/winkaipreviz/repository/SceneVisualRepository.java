package ru.wink.winkaipreviz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wink.winkaipreviz.entity.SceneVisualEntity;

import java.util.Optional;
import java.util.UUID;

public interface SceneVisualRepository extends JpaRepository<SceneVisualEntity, Long> {

    Optional<SceneVisualEntity> findBySceneId(UUID sceneId);
}

