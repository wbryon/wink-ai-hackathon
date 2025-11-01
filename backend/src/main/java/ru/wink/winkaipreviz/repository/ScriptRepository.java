package ru.wink.winkaipreviz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wink.winkaipreviz.entity.Script;

import java.util.UUID;

public interface ScriptRepository extends JpaRepository<Script, UUID> {
}
