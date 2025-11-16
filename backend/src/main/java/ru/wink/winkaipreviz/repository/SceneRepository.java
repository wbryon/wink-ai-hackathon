package ru.wink.winkaipreviz.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.wink.winkaipreviz.entity.Scene;
import ru.wink.winkaipreviz.entity.SceneStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface SceneRepository extends JpaRepository<Scene, UUID> {
    @EntityGraph(attributePaths = "frames")
    List<Scene> findByScript_Id(UUID scriptId);

    /**
     * Сцены сценария в стабильном порядке — по времени создания (порядок парсинга/загрузки).
     * Используется для отображения списков сцен "сверху вниз" от первой к последней.
     */
    @EntityGraph(attributePaths = "frames")
    List<Scene> findByScript_IdOrderByCreatedAtAsc(UUID scriptId);

    boolean existsByScript_IdAndExternalId(UUID scriptId, String externalId);

    boolean existsByScript_IdAndDedupHash(UUID scriptId, String dedupHash);

    long countByScript_IdAndStatus(UUID scriptId, SceneStatus status);
}
