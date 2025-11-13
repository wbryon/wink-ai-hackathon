-- Добавляем связь parent Frame для progressive path (Sketch → Mid → Final)
ALTER TABLE frames ADD COLUMN parent_frame_id UUID REFERENCES frames(id) ON DELETE SET NULL;

-- Индекс для быстрого поиска дочерних кадров
CREATE INDEX IF NOT EXISTS idx_frames_parent ON frames(parent_frame_id);

-- Комментарий к колонке
COMMENT ON COLUMN frames.parent_frame_id IS 'Ссылка на родительский кадр для progressive path (например, Sketch для Mid, Mid для Final)';

