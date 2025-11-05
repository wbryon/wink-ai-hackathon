-- Scripts
CREATE TABLE IF NOT EXISTS scripts (
                                       id UUID PRIMARY KEY,
                                       filename VARCHAR(512) NOT NULL,
    status VARCHAR(64) NOT NULL,
    file_path TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
    );

-- Scenes
CREATE TABLE IF NOT EXISTS scenes (
                                      id UUID PRIMARY KEY,
                                      script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    title VARCHAR(512),
    location VARCHAR(512),
    description TEXT,
    prompt TEXT,
    created_at TIMESTAMPTZ NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_scenes_script ON scenes(script_id);

-- Scene characters
CREATE TABLE IF NOT EXISTS scene_characters (
                                                scene_id UUID NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    character TEXT NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_scene_characters_scene ON scene_characters(scene_id);

-- Scene props
CREATE TABLE IF NOT EXISTS scene_props (
                                           scene_id UUID NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    prop TEXT NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_scene_props_scene ON scene_props(scene_id);

-- Frames
CREATE TABLE IF NOT EXISTS frames (
                                      id UUID PRIMARY KEY,
                                      scene_id UUID NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    image_url TEXT,
    detail_level VARCHAR(32),
    prompt TEXT,
    seed INTEGER,
    created_at TIMESTAMPTZ NOT NULL
    );
CREATE INDEX IF NOT EXISTS idx_frames_scene_created ON frames(scene_id, created_at DESC);