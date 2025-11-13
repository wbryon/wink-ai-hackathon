-- Add original_json column to scenes to store the original JSON from parser
ALTER TABLE scenes
    ADD COLUMN IF NOT EXISTS original_json TEXT;

