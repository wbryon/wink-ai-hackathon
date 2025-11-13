# AI Image Generation Service (ComfyUI-backed)

Сервис для генерации изображений через **ComfyUI**, обёрнутый в **FastAPI**.

- Никакого `torch`, `diffusers`, моделей и CUDA внутри сервиса.
- Все тяжёлые вещи (модели, генерация, GPU) живут в **ComfyUI**.
- Этот сервис только:
  - принимает запросы `/generate` и `/img2img`,
  - маппит параметры (`lod`, `steps`, `cfg`, `sampler`, `seed`, …) на ComfyUI workflow,
  - шлёт их в ComfyUI (`/prompt`, `/history`, `/view`, `/upload/image`),
  - сохраняет результат в папку `/data/frames`,
  - отдаёт URL вида `/images/...`.

---

## Архитектура

Компоненты:

- `main.py` — FastAPI-приложение (`/generate`, `/img2img`, `/health`, `/images/{filename}`).
- `config.py` — конфиг:
  - LOD-профили (`sketch`, `mid`, `final`, `direct_final`);
  - пути к JSON-workflow’ам для ComfyUI;
  - настройки подключения к ComfyUI;
  - директория для сохранения картинок.
- `comfy_client.py` — клиент к ComfyUI:
  - `POST /prompt` — постановка джобы;
  - `GET /history/{prompt_id}` — ожидание результата;
  - `GET /view` — скачивание изображения;
  - `POST /upload/image` — загрузка исходной картинки для img2img.
- `image_generator.py` — тонкий слой:
  - применяет LOD-профили;
  - считает длительность;
  - сохраняет картинку на диск;
  - возвращает удобный словарь с полями `image_url`, `steps`, `cfg`, и т.д.
- `workflows/` — JSON-файлы workflow’ов ComfyUI в формате **Save (API Format)**:
  - `workflows/text2img.json`
  - `workflows/img2img.json`

---

## Требования

1. **Запущенный ComfyUI** с включённым API (по умолчанию `http://localhost:8188` или `http://comfyui:8188` в docker-compose).
2. В ComfyUI настроены и сохранены два workflow’а:
   - `workflows/text2img.json` — text2img workflow (KSampler + EmptyLatentImage).
   - `workflows/img2img.json` — img2img workflow (KSampler + LoadImage).
3. Python 3.11+ (если запускаешь без Docker) или Docker.

---

## Примеры ComfyUI workflow’ов

### 1. Пример text2img workflow

**Цель:** базовый граф для генерации “с нуля” (text2img), под который заточен сервис.  

**Ноды (в ComfyUI UI):**

- `CheckpointLoaderSimple`
- `CLIP Text Encode` (positive)
- `CLIP Text Encode` (negative)
- `Empty Latent Image`
- `KSampler`
- `VAE Decode`
- `Save Image`

**Соединения:**

- `CheckpointLoaderSimple.MODEL` → `KSampler.model`
- `CheckpointLoaderSimple.CLIP` → оба `CLIPTextEncode.clip`
- `CheckpointLoaderSimple.VAE` → `VAEDecode.vae`
- `CLIPTextEncode (positive).CONDITIONING` → `KSampler.positive`
- `CLIPTextEncode (negative).CONDITIONING` → `KSampler.negative`
- `EmptyLatentImage.LATENT` → `KSampler.latent_image`
- `KSampler.LATENT` → `VAEDecode.samples`
- `VAEDecode.IMAGE` → `SaveImage.images`

**Пример JSON (`workflows/text2img.json`)**

> Это пример структуры, твой `ckpt_name` / id нод могут отличаться. Важно: `class_type`.

```json
{
  "1": {
    "class_type": "CheckpointLoaderSimple",
    "inputs": {
      "ckpt_name": "sd_xl_base_1.0.safetensors"
    }
  },
  "2": {
    "class_type": "CLIPTextEncode",
    "inputs": {
      "clip": ["1", 1],
      "text": "placeholder positive"
    }
  },
  "3": {
    "class_type": "CLIPTextEncode",
    "inputs": {
      "clip": ["1", 1],
      "text": "placeholder negative"
    }
  },
  "4": {
    "class_type": "EmptyLatentImage",
    "inputs": {
      "width": 1024,
      "height": 640,
      "batch_size": 1
    }
  },
  "5": {
    "class_type": "KSampler",
    "inputs": {
      "seed": 1,
      "steps": 20,
      "cfg": 7.0,
      "sampler_name": "euler_ancestral",
      "scheduler": "normal",
      "denoise": 1.0,
      "model": ["1", 0],
      "positive": ["2", 0],
      "negative": ["3", 0],
      "latent_image": ["4", 0]
    }
  },
  "6": {
    "class_type": "VAEDecode",
    "inputs": {
      "samples": ["5", 0],
      "vae": ["1", 2]
    }
  },
  "7": {
    "class_type": "SaveImage",
    "inputs": {
      "images": ["6", 0],
      "filename_prefix": "text2img"
    }
  }
}
