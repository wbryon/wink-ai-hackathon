-- Add enriched_json column to frames to match entity `Frame.enrichedJson`
ALTER TABLE frames
    ADD COLUMN IF NOT EXISTS enriched_json TEXT;
