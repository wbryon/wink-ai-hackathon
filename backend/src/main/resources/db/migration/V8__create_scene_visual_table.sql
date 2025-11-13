-- Create scene_visual table for storing enriched JSON and Flux prompts
-- This migration creates the table with correct structure (scene_id as UUID)
-- If table already exists with wrong type (BIGINT), it will be fixed in next migration

CREATE TABLE IF NOT EXISTS scene_visual (
    id BIGSERIAL PRIMARY KEY,
    scene_id UUID NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
    enriched_json JSONB NOT NULL,
    flux_prompt TEXT NOT NULL,
    image_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PROMPT_READY',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scene_visual_scene_id ON scene_visual(scene_id);
CREATE INDEX IF NOT EXISTS idx_scene_visual_status ON scene_visual(status);

-- Fix scene_id type if table was created earlier with wrong type (BIGINT)
-- This handles the case where table was auto-created by Hibernate with wrong type
DO $$
BEGIN
    IF EXISTS (
        SELECT FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'scene_visual' 
        AND column_name = 'scene_id' 
        AND data_type = 'bigint'
    ) THEN
        -- Delete all existing data since we can't convert BIGINT to UUID safely
        DELETE FROM scene_visual;
        
        -- Drop the existing constraint/index if exists
        ALTER TABLE scene_visual DROP CONSTRAINT IF EXISTS scene_visual_scene_id_fkey;
        DROP INDEX IF EXISTS idx_scene_visual_scene_id;
        
        -- Drop and recreate column with correct type
        ALTER TABLE scene_visual DROP COLUMN scene_id;
        ALTER TABLE scene_visual ADD COLUMN scene_id UUID NOT NULL REFERENCES scenes(id) ON DELETE CASCADE;
        
        -- Recreate index
        CREATE INDEX IF NOT EXISTS idx_scene_visual_scene_id ON scene_visual(scene_id);
    END IF;
END $$;

