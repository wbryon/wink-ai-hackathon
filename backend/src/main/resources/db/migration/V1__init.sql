-- Scripts table
CREATE TABLE IF NOT EXISTS scripts (
    id UUID PRIMARY KEY,
    filename VARCHAR(512) NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Scenes table
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

-- Frames table
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

-- Scripts table
CREATE TABLE IF NOT EXISTS scripts (
    id VARCHAR(64) PRIMARY KEY,
    filename TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Scenes table
CREATE TABLE IF NOT EXISTS scenes (
    id VARCHAR(64) PRIMARY KEY,
    script_id VARCHAR(64) NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    title TEXT,
    location TEXT,
    description TEXT,
    prompt TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_scenes_script_id ON scenes(script_id);

-- Scene characters
CREATE TABLE IF NOT EXISTS scene_characters (
    scene_id VARCHAR(64) NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    value TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_scene_characters_scene_id ON scene_characters(scene_id);

-- Scene props
CREATE TABLE IF NOT EXISTS scene_props (
    scene_id VARCHAR(64) NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    value TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_scene_props_scene_id ON scene_props(scene_id);

-- Frames table
CREATE TABLE IF NOT EXISTS frames (
    id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    image_url TEXT,
    detail_level VARCHAR(16),
    prompt TEXT,
    seed INTEGER,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_frames_scene_id ON frames(scene_id);


