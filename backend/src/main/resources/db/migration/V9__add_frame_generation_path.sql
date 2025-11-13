-- Добавляем колонку generation_path для хранения пути генерации кадра (DIRECT/PROGRESSIVE)
ALTER TABLE frames
    ADD COLUMN IF NOT EXISTS generation_path VARCHAR(16);


