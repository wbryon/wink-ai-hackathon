ALTER TABLE scenes
    ADD COLUMN IF NOT EXISTS external_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS dedup_hash VARCHAR(64);

-- Идемпотентные уникальные индексы в пределах сценария
CREATE UNIQUE INDEX IF NOT EXISTS ux_scenes_script_external
    ON scenes (script_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_scenes_script_dedup
    ON scenes (script_id, dedup_hash)
    WHERE dedup_hash IS NOT NULL;


