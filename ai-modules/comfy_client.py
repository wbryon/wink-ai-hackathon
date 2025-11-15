# comfy_client.py
"""
Thin client around ComfyUI HTTP API.

Использует эндпоинты ComfyUI:
- POST /prompt
- GET  /history/{prompt_id}
- GET  /view
- POST /upload/image

Работает синхронно (requests), чтобы удобно вызывать из FastAPI sync endpoints.
"""

import io
import json
import logging
import time
import uuid
from pathlib import Path
from typing import Dict, Any, Tuple, Optional
from urllib.parse import urlencode

import requests

from config import (
    COMFY_BASE_URL,
    TEXT2IMG_WORKFLOW_PATH,
    IMG2IMG_WORKFLOW_PATH,
)

LOG = logging.getLogger("comfy_client")

_CLIENT_ID = str(uuid.uuid4())

_text2img_workflow_cache: Optional[Dict[str, Any]] = None
_img2img_workflow_cache: Optional[Dict[str, Any]] = None


def _load_workflow(path: Path) -> Dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(
            f"ComfyUI workflow JSON not found at {path}. "
            f"Сохрани его из ComfyUI: Save → Save (API Format)."
        )
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def get_text2img_workflow() -> Dict[str, Any]:
    global _text2img_workflow_cache
    if _text2img_workflow_cache is None:
        _text2img_workflow_cache = _load_workflow(TEXT2IMG_WORKFLOW_PATH)
    return _text2img_workflow_cache


def get_img2img_workflow() -> Dict[str, Any]:
    global _img2img_workflow_cache
    if _img2img_workflow_cache is None:
        _img2img_workflow_cache = _load_workflow(IMG2IMG_WORKFLOW_PATH)
    return _img2img_workflow_cache


# Маппинг имён с твоих LOD'ов на ComfyUI sampler_name
SAMPLER_NAME_MAP = {
    "euler_a": "euler_ancestral",
    "euler_ancestral": "euler_ancestral",
    "euler": "euler",
    "dpm++": "dpmpp_2m",
    "dpmpp": "dpmpp_2m",
    "dpmpp_2m": "dpmpp_2m",
}


def _queue_prompt(prompt: Dict[str, Any]) -> str:
    """POST /prompt → prompt_id."""
    url = f"{COMFY_BASE_URL}/prompt"
    payload = {"prompt": prompt, "client_id": _CLIENT_ID}
    resp = requests.post(url, json=payload, timeout=60)
    resp.raise_for_status()
    data = resp.json()
    prompt_id = data.get("prompt_id")
    if not prompt_id:
        raise RuntimeError(f"ComfyUI /prompt answered without prompt_id: {data}")
    return prompt_id


def _get_history(prompt_id: str) -> Dict[str, Any]:
    """GET /history/{prompt_id}."""
    url = f"{COMFY_BASE_URL}/history/{prompt_id}"
    resp = requests.get(url, timeout=60)
    resp.raise_for_status()
    return resp.json()


def _get_image(filename: str, subfolder: str, folder_type: str) -> bytes:
    """GET /view?filename=...&subfolder=...&type=... → raw image bytes."""
    params = {"filename": filename, "subfolder": subfolder, "type": folder_type}
    url = f"{COMFY_BASE_URL}/view?{urlencode(params)}"
    resp = requests.get(url, timeout=60)
    resp.raise_for_status()
    return resp.content


def _upload_image(path: Path, image_type: str = "input", overwrite: bool = True) -> str:
    """
    POST /upload/image.

    Возвращаем имя файла, которое надо подставлять в LoadImage.
    """
    url = f"{COMFY_BASE_URL}/upload/image"
    files = {
        "image": (path.name, path.open("rb"), "image/png"),
    }
    data = {
        "type": image_type,
        "overwrite": str(overwrite).lower(),
    }
    resp = requests.post(url, data=data, files=files, timeout=120)
    resp.raise_for_status()
    # Сейчас ComfyUI всё равно кладёт под тем же именем.
    return path.name


def _wait_for_first_output_image(prompt_id: str, timeout_sec: int = 300) -> Tuple[bytes, Dict[str, Any]]:
    """
    Ожидаем, пока в history не появятся output-изображения, и забираем первое.
    """
    start = time.time()
    while True:
        history_all = _get_history(prompt_id)
        history = history_all.get(prompt_id)
        if history and "outputs" in history:
            outputs = history["outputs"]
            for node_id, node_output in outputs.items():
                images = node_output.get("images") or []
                for img in images:
                    if img.get("type") == "output":
                        img_bytes = _get_image(
                            filename=img["filename"],
                            subfolder=img.get("subfolder", ""),
                            folder_type=img.get("type", "output"),
                        )
                        meta = {
                            "filename": img["filename"],
                            "subfolder": img.get("subfolder", ""),
                            "type": img.get("type", "output"),
                            "node_id": node_id,
                        }
                        return img_bytes, meta

        if time.time() - start > timeout_sec:
            raise TimeoutError(f"Timed out waiting for ComfyUI output for prompt_id={prompt_id}")

        time.sleep(1.0)


def _build_text2img_prompt(
    base_workflow: Dict[str, Any],
    prompt_text: str,
    negative_text: str,
    steps: int,
    cfg: float,
    sampler_name: str,
    width: int,
    height: int,
    seed: int,
) -> Dict[str, Any]:
    """
    На основе сохранённого в JSON workflow'а:
    - ищем KSampler
    - подставляем seed/cfg/steps/sampler
    - меняем текст в CLIPTextEncode для positive и negative
    - если есть EmptyLatentImage, меняем width/height
    """
    import copy

    workflow = copy.deepcopy(base_workflow)
    id_to_class = {nid: node["class_type"] for nid, node in workflow.items()}

    # KSampler
    ksampler_id = next(nid for nid, cls in id_to_class.items() if cls == "KSampler")
    ksampler = workflow[ksampler_id]["inputs"]

    comfy_sampler = SAMPLER_NAME_MAP.get(sampler_name, sampler_name)
    ksampler["sampler_name"] = comfy_sampler
    ksampler["scheduler"] = ksampler.get("scheduler", "normal")
    ksampler["steps"] = steps
    ksampler["cfg"] = cfg
    ksampler["seed"] = seed

    # Latent node → поменять ширину / высоту
    latent_node_id = ksampler["latent_image"][0]
    latent_node = workflow[latent_node_id]
    if latent_node["class_type"] == "EmptyLatentImage":
        latent_inputs = latent_node["inputs"]
        latent_inputs["width"] = width
        latent_inputs["height"] = height

    # Positive / Negative CLIPTextEncode
    positive_node_id = ksampler["positive"][0]
    workflow[positive_node_id]["inputs"]["text"] = prompt_text

    if negative_text:
        negative_node_id = ksampler["negative"][0]
        workflow[negative_node_id]["inputs"]["text"] = negative_text

    return workflow


def _build_img2img_prompt(
    base_workflow: Dict[str, Any],
    prompt_text: str,
    negative_text: str,
    steps: int,
    cfg: float,
    sampler_name: str,
    denoise: float,
    seed: int,
    image_filename: str,
) -> Dict[str, Any]:
    """
    Для img2img:
    - ищем KSampler → steps/cfg/seed/sampler/denoise
    - positive/negative → CLIPTextEncode
    - LoadImage → подставляем имя файла, который загрузили через /upload/image
    """
    import copy

    workflow = copy.deepcopy(base_workflow)
    id_to_class = {nid: node["class_type"] for nid, node in workflow.items()}

    ksampler_id = next(nid for nid, cls in id_to_class.items() if cls == "KSampler")
    ksampler = workflow[ksampler_id]["inputs"]

    comfy_sampler = SAMPLER_NAME_MAP.get(sampler_name, sampler_name)
    ksampler["sampler_name"] = comfy_sampler
    ksampler["scheduler"] = ksampler.get("scheduler", "normal")
    ksampler["steps"] = steps
    ksampler["cfg"] = cfg
    ksampler["seed"] = seed
    ksampler["denoise"] = denoise

    positive_node_id = ksampler["positive"][0]
    workflow[positive_node_id]["inputs"]["text"] = prompt_text

    if negative_text:
        negative_node_id = ksampler["negative"][0]
        workflow[negative_node_id]["inputs"]["text"] = negative_text

    # LoadImage
    load_image_id = next(nid for nid, cls in id_to_class.items() if cls == "LoadImage")
    workflow[load_image_id]["inputs"]["image"] = image_filename

    return workflow


def run_text2img(
    prompt: str,
    negative_prompt: str,
    steps: int,
    cfg: float,
    sampler: str,
    width: int,
    height: int,
    seed: int,
) -> bytes:
    """
    Высокоуровневая функция:
    - собирает prompt JSON
    - отправляет в ComfyUI
    - ждёт историю
    - возвращает байты первой output-картинки.
    """
    base_workflow = get_text2img_workflow()

    full_prompt = _build_text2img_prompt(
        base_workflow=base_workflow,
        prompt_text=prompt,
        negative_text=negative_prompt,
        steps=steps,
        cfg=cfg,
        sampler_name=sampler,
        width=width,
        height=height,
        seed=seed,
    )

    prompt_id = _queue_prompt(full_prompt)
    LOG.info(f"Queued text2img prompt to ComfyUI, prompt_id={prompt_id}")
    img_bytes, meta = _wait_for_first_output_image(prompt_id)
    LOG.info(f"Received image from ComfyUI: {meta}")
    return img_bytes


def run_img2img(
    prompt: str,
    negative_prompt: str,
    init_image_path_or_url: str,
    denoise: float,
    steps: int,
    cfg: float,
    sampler: str,
    seed: int,
) -> bytes:
    """
    Img2img через ComfyUI:
    - при необходимости скачивает init image из URL
    - загружает в ComfyUI через /upload/image
    - собирает workflow JSON
    - ждёт результат.
    """
    import tempfile

    # Подготовим локальный файл
    if init_image_path_or_url.startswith("http://") or init_image_path_or_url.startswith("https://"):
        resp = requests.get(init_image_path_or_url, timeout=60)
        resp.raise_for_status()
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
            tmp.write(resp.content)
            tmp_path = Path(tmp.name)
    else:
        tmp_path = Path(init_image_path_or_url)
        if not tmp_path.exists():
            raise FileNotFoundError(f"Init image not found: {tmp_path}")

    # Загружаем в ComfyUI
    filename_in_comfy = _upload_image(tmp_path, image_type="input", overwrite=True)
    LOG.info(f"Uploaded init image to ComfyUI as {filename_in_comfy}")

    base_workflow = get_img2img_workflow()
    full_prompt = _build_img2img_prompt(
        base_workflow=base_workflow,
        prompt_text=prompt,
        negative_text=negative_prompt,
        steps=steps,
        cfg=cfg,
        sampler_name=sampler,
        denoise=denoise,
        seed=seed,
        image_filename=filename_in_comfy,
    )

    prompt_id = _queue_prompt(full_prompt)
    LOG.info(f"Queued img2img prompt to ComfyUI, prompt_id={prompt_id}")
    img_bytes, meta = _wait_for_first_output_image(prompt_id)
    LOG.info(f"Received img2img image from ComfyUI: {meta}")
    return img_bytes
