package ru.wink.winkaipreviz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wink.winkaipreviz.entity.Frame;

import java.util.List;
import java.util.UUID;

public interface FrameRepository extends JpaRepository<Frame, UUID> {
	List<Frame> findByScene_IdOrderByCreatedAtDesc(UUID sceneId);
}
