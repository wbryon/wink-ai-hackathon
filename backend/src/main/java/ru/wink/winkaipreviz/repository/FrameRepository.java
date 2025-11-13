package ru.wink.winkaipreviz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wink.winkaipreviz.entity.DetailLevel;
import ru.wink.winkaipreviz.entity.Frame;

import java.util.List;
import java.util.UUID;

public interface FrameRepository extends JpaRepository<Frame, UUID> {
	List<Frame> findByScene_IdOrderByCreatedAtDesc(UUID sceneId);
	
	/**
	 * Находит кадры по сцене и уровню детализации, отсортированные по дате создания (новые первые).
	 */
	List<Frame> findByScene_IdAndDetailLevelOrderByCreatedAtDesc(UUID sceneId, DetailLevel detailLevel);
}
