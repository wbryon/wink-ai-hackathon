-- Scenes: add tone/style/semantic_summary/status
ALTER TABLE scenes
    ADD COLUMN IF NOT EXISTS tone VARCHAR(128),
    ADD COLUMN IF NOT EXISTS style VARCHAR(128),
    ADD COLUMN IF NOT EXISTS semantic_summary TEXT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'PARSED' NOT NULL;

-- Scripts: add text_extracted and parsed_json
ALTER TABLE scripts
    ADD COLUMN IF NOT EXISTS text_extracted TEXT,
    ADD COLUMN IF NOT EXISTS parsed_json TEXT;

-- Frames: add model, generation time (ms) and is_best flag
ALTER TABLE frames
    ADD COLUMN IF NOT EXISTS model VARCHAR(128),
    ADD COLUMN IF NOT EXISTS generation_ms BIGINT,
    ADD COLUMN IF NOT EXISTS is_best BOOLEAN DEFAULT FALSE;


