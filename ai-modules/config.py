# config.py
"""
Configuration for AI image generation service that delegates work to ComfyUI.

- LOD-профили (steps/cfg/разрешение/negatives)
- Пути к workflow'ам ComfyUI
- Настройки подключения к ComfyUI
- Директория хранения готовых картинок
"""

import os
from typing import Optional, Dict, Any
from pathlib import Path

# Директория для сохранения сгенерированных изображений
STORAGE_DIR = Path(os.getenv("STORAGE_DIR", "/data/frames"))
try:
    STORAGE_DIR.mkdir(parents=True, exist_ok=True)
except Exception:
    # В Docker создадим в Dockerfile / при старте
    pass

# Базовый URL, по которому клиенты будут забирать изображения
IMAGE_URL_BASE = os.getenv("IMAGE_URL_BASE", "http://localhost:8000/images")

# ComfyUI connection settings
COMFY_HOST = os.getenv("COMFY_HOST", "127.0.0.1")
COMFY_PORT = int(os.getenv("COMFY_PORT", "8188"))
COMFY_BASE_URL = os.getenv("COMFY_BASE_URL", f"http://{COMFY_HOST}:{COMFY_PORT}")

# Директория, где лежат сохранённые из ComfyUI workflow'ы (Save → Save (API Format))
WORKFLOWS_DIR = Path(os.getenv("WORKFLOWS_DIR", "./workflows"))

# Основной workflow для text2img — теперь используем flux_schnell.json
# (экспортированный из ComfyUI в формате Save (API Format))
TEXT2IMG_WORKFLOW_PATH = WORKFLOWS_DIR / "flux_schnell.json"

# Img2img по‑прежнему может использовать отдельный workflow (при необходимости можно
# также перенести на flux_schnell.json или другой файл через переменные окружения).
IMG2IMG_WORKFLOW_PATH = WORKFLOWS_DIR / "img2img.json"

# LOD Profiles configuration (длинная сторона 720 px)
LOD_PROFILES: Dict[str, Dict[str, Any]] = {
    "sketch": {
        "steps": 12,
        "cfg": 3.5,
        # Для Sketch берём 720 по длинной стороне, высота 576 (4:3)
        "resolution": (720, 576),
        "sampler": "euler_a",
        "negatives": ["colors", "fine textures", "typography", "text watermark"],
    },
    "mid": {
        "steps": 25,
        "cfg": 5.0,
        # Для Mid также фиксируем длинную сторону 720 px
        "resolution": (720, 576),
        "sampler": "euler_a",
        "negatives": ["ultra-detailed skin pores", "complex patterns", "watermark", "low-res"],
        "denoise_range": (0.25, 0.45),
    },
    "final": {
        "steps": 40,
        "cfg": 6.5,
        # Финальный кадр: те же пропорции, длинная сторона 720 px
        "resolution": (720, 576),
        "sampler": "dpm++",
        "negatives": ["low-res", "extra fingers", "text", "artifact", "over-sharpen", "blurry"],
        "denoise_range": (0.35, 0.55),
    },
    "direct_final": {
        "steps": 35,
        "cfg": 7.0,
        # Direct Final: длинная сторона 720 px
        "resolution": (720, 576),
        "sampler": "dpm++",
        "negatives": ["low-res", "extra fingers", "text", "artifact", "complex patterns", "watermark"],
    },
}


def get_lod_profile(lod: str) -> Dict[str, Any]:
    """Get LOD profile configuration."""
    lod_lower = lod.lower()
    if lod_lower not in LOD_PROFILES:
        raise ValueError(f"Unknown LOD: {lod}. Available: {list(LOD_PROFILES.keys())}")
    return LOD_PROFILES[lod_lower]

# Важно: нужно из ComfyUI GUI сохранить два workflow'а в формате Save (API Format):
# ./workflows/text2img.json
# ./workflows/img2img.json
# и настроить их так, чтобы там был KSampler, EmptyLatentImage (для text2img) и LoadImage (для img2img), как в примерах ниже.