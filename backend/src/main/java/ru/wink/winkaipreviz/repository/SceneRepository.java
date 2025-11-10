package ru.wink.winkaipreviz.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.wink.winkaipreviz.entity.Scene;

import java.util.List;
import java.util.UUID;

@Repository
public interface SceneRepository extends JpaRepository<Scene, UUID> {
    @EntityGraph(attributePaths = "frames")
    List<Scene> findByScript_Id(UUID scriptId);

    boolean existsByScript_IdAndExternalId(UUID scriptId, String externalId);

    boolean existsByScript_IdAndDedupHash(UUID scriptId, String dedupHash);
}
