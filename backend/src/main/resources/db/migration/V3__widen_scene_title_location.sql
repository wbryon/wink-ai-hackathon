-- Widen scene title/location to avoid overflow from rich headers
ALTER TABLE IF EXISTS scenes
  ALTER COLUMN title TYPE TEXT,
  ALTER COLUMN location TYPE TEXT;


