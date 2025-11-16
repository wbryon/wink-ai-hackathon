# image_generator.py
"""
Слой над ComfyUI-клиентом, который:
- применяет LOD профили
- считает длительность генерации
- сохраняет результат в STORAGE_DIR
- возвращает метаданные в удобном виде (как раньше).
"""

import time
import uuid
import logging
import random
from typing import Optional, Dict, Any, Tuple

from pathlib import Path

from config import (
    get_lod_profile,
    STORAGE_DIR,
    IMAGE_URL_BASE,
)
import comfy_client

LOG = logging.getLogger("image_generator")


def _ensure_storage_dir() -> None:
    try:
        STORAGE_DIR.mkdir(parents=True, exist_ok=True)
    except Exception:
        pass


def generate_text2img(
    prompt: str,
    negative_prompt: str,
    lod: str,
    seed: Optional[int] = None,
    steps: Optional[int] = None,
    cfg: Optional[float] = None,
    resolution: Optional[Tuple[int, int]] = None,
    sampler: Optional[str] = None,
    model_name: Optional[str] = None,  # игнорируем, логика теперь в ComfyUI
) -> Dict[str, Any]:
    """
    Generate image from text prompt (text2img) через ComfyUI.

    Возвращает:
        Dict с ключами: image_path, image_url, seed, steps, cfg, resolution, sampler, duration_ms
    """
    start_time = time.time()
    _ensure_storage_dir()

    lod_profile = get_lod_profile(lod)

    final_steps = steps or lod_profile["steps"]
    final_cfg = cfg or lod_profile["cfg"]
    final_resolution = resolution or lod_profile["resolution"]
    final_sampler = sampler or lod_profile.get("sampler", "euler_a")

    if seed is None:
        seed = random.randint(0, 2**32 - 1)

    width, height = final_resolution

    LOG.info(
        f"Text2img via ComfyUI: lod={lod}, steps={final_steps}, cfg={final_cfg}, "
        f"resolution={final_resolution}, sampler={final_sampler}, seed={seed}"
    )

    # Генерация через ComfyUI
    image_bytes = comfy_client.run_text2img(
        prompt=prompt,
        negative_prompt=negative_prompt,
        steps=final_steps,
        cfg=final_cfg,
        sampler=final_sampler,
        width=width,
        height=height,
        seed=seed,
    )

    # Сохраняем на диск, как раньше
    frame_id = f"frame_{uuid.uuid4().hex[:8]}"
    image_filename = f"{frame_id}.png"
    image_path = STORAGE_DIR / image_filename
    image_path.write_bytes(image_bytes)

    duration_ms = int((time.time() - start_time) * 1000)
    image_url = f"{IMAGE_URL_BASE}/{image_filename}"

    return {
        "image_path": str(image_path),
        "image_url": image_url,
        "seed": seed,
        "model": model_name or "comfyui-workflow",
        "steps": final_steps,
        "cfg": final_cfg,
        "resolution": final_resolution,
        "sampler": final_sampler,
        "duration_ms": duration_ms,
    }


def generate_img2img(
    prompt: str,
    negative_prompt: str,
    init_image_path: str,
    denoise_strength: float,
    lod: str,
    seed: Optional[int] = None,
    steps: Optional[int] = None,
    cfg: Optional[float] = None,
    resolution: Optional[Tuple[int, int]] = None,  # не используется, размер берётся из init image
    sampler: Optional[str] = None,
    model_name: Optional[str] = None,
) -> Dict[str, Any]:
    """
    Generate image from existing image (img2img) через ComfyUI.

    Args:
        init_image_path: путь или URL к исходной картинке
        denoise_strength: 0.0-1.0

    Returns:
        Dict с ключами: image_path, image_url, seed, steps, cfg, denoise, duration_ms
    """
    start_time = time.time()
    _ensure_storage_dir()

    lod_profile = get_lod_profile(lod)

    final_steps = steps or lod_profile["steps"]
    final_cfg = cfg or lod_profile["cfg"]
    final_sampler = sampler or lod_profile.get("sampler", "euler_a")

    if denoise_strength is None:
        denoise_range = lod_profile.get("denoise_range", (0.3, 0.5))
        denoise_strength = (denoise_range[0] + denoise_range[1]) / 2

    if seed is None:
        seed = random.randint(0, 2**32 - 1)

    LOG.info(
        f"Img2img via ComfyUI: lod={lod}, steps={final_steps}, cfg={final_cfg}, "
        f"denoise={denoise_strength}, sampler={final_sampler}, seed={seed}"
    )

    image_bytes = comfy_client.run_img2img(
        prompt=prompt,
        negative_prompt=negative_prompt,
        init_image_path_or_url=init_image_path,
        denoise=denoise_strength,
        steps=final_steps,
        cfg=final_cfg,
        sampler=final_sampler,
        seed=seed,
    )

    frame_id = f"frame_{uuid.uuid4().hex[:8]}"
    image_filename = f"{frame_id}.png"
    image_path = STORAGE_DIR / image_filename
    image_path.write_bytes(image_bytes)

    duration_ms = int((time.time() - start_time) * 1000)
    image_url = f"{IMAGE_URL_BASE}/{image_filename}"

    return {
        "image_path": str(image_path),
        "image_url": image_url,
        "seed": seed,
        "model": model_name or "comfyui-workflow",
        "steps": final_steps,
        "cfg": final_cfg,
        "denoise": denoise_strength,
        "duration_ms": duration_ms,
    }
