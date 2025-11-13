# main.py
from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from typing import Dict, Any, Optional, List
import logging

from config import STORAGE_DIR, get_lod_profile
from image_generator import generate_text2img, generate_img2img

# Configure logging
logging.basicConfig(level=logging.INFO)
LOG = logging.getLogger("ai-modules")

app = FastAPI(title="AI Image Generation Service (ComfyUI-backed)")

# Static files for serving generated images
STORAGE_DIR.mkdir(parents=True, exist_ok=True)
app.mount("/images", StaticFiles(directory=str(STORAGE_DIR)), name="images")


class GenerateRequest(BaseModel):
    """Request for text2img generation."""
    scene_id: Optional[str] = None
    prompt: str = Field(..., description="Positive prompt for generation")
    negative_prompt: Optional[str] = Field(None, description="Negative prompt")
    lod: str = Field("sketch", description="Level of detail: sketch, mid, final, direct_final")
    seed: Optional[int] = Field(None, description="Seed for reproducibility")
    steps: Optional[int] = Field(None, description="Number of inference steps (overrides LOD default)")
    cfg: Optional[float] = Field(None, description="CFG scale (overrides LOD default)")
    resolution: Optional[List[int]] = Field(
        None,
        description="Resolution [width, height] (overrides LOD default)"
    )
    sampler: Optional[str] = Field(None, description="Sampler name (euler_a, dpm++, etc.)")
    model: Optional[str] = Field(None, description="Model name (sdxl, flux, etc.) — сейчас игнорируется, всё в ComfyUI")


class Img2ImgRequest(BaseModel):
    """Request for img2img generation."""
    scene_id: Optional[str] = None
    prompt: str = Field(..., description="Positive prompt for generation")
    negative_prompt: Optional[str] = Field(None, description="Negative prompt")
    image_url: str = Field(..., description="URL or path to initial image")
    denoise: Optional[float] = Field(None, description="Denoise strength (0.0-1.0)")
    lod: str = Field("mid", description="Level of detail: sketch, mid, final")
    seed: Optional[int] = Field(None, description="Seed for reproducibility")
    steps: Optional[int] = Field(None, description="Number of inference steps (overrides LOD default)")
    cfg: Optional[float] = Field(None, description="CFG scale (overrides LOD default)")
    sampler: Optional[str] = Field(None, description="Sampler name")
    model: Optional[str] = Field(None, description="Model name (игнорируется, всё в ComfyUI)")


class ParseRequest(BaseModel):
    text: str


@app.get("/health")
def health():
    """Health check endpoint."""
    return {
        "status": "ok",
        "service": "ai-image-generation",
        "storage_dir": str(STORAGE_DIR),
        "storage_exists": STORAGE_DIR.exists(),
    }


@app.post("/generate")
def generate(req: GenerateRequest) -> Dict[str, Any]:
    """
    Generate image from text prompt (text2img) via ComfyUI.

    Поддерживает LOD профили (sketch, mid, final, direct_final) с автоматическим
    выбором параметров, либо ручной override steps/cfg/resolution/sampler.
    """
    try:
        lod_profile = get_lod_profile(req.lod)

        negative_prompt = req.negative_prompt or ", ".join(lod_profile.get("negatives", []))

        resolution = (
            tuple(req.resolution)
            if req.resolution and len(req.resolution) == 2
            else None
        )

        result = generate_text2img(
            prompt=req.prompt,
            negative_prompt=negative_prompt,
            lod=req.lod,
            seed=req.seed,
            steps=req.steps,
            cfg=req.cfg,
            resolution=resolution,
            sampler=req.sampler,
            model_name=req.model,
        )

        response = {
            "scene_id": req.scene_id,
            "lod": req.lod,
            "image_url": result["image_url"],
            "model": result["model"],
            "seed": result["seed"],
            "steps": result["steps"],
            "cfg": result["cfg"],
            "resolution": result["resolution"],
            "sampler": result["sampler"],
            "duration_ms": result["duration_ms"],
        }

        LOG.info(
            f"Generated image via ComfyUI: {result['image_url']} "
            f"(seed={result['seed']}, steps={result['steps']})"
        )
        return response

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(
            status_code=500,
            detail=f"Workflow or image not found: {e}",
        )
    except TimeoutError as e:
        raise HTTPException(status_code=504, detail=str(e))
    except Exception as e:
        LOG.exception("Generation failed")
        raise HTTPException(status_code=500, detail=f"Generation failed: {str(e)}")


@app.post("/img2img")
def img2img(req: Img2ImgRequest) -> Dict[str, Any]:
    """
    Generate image from existing image (img2img) via ComfyUI.

    Используется для прогрессивного пути: Sketch → Mid → Final.
    Требует URL/путь к исходному изображению и denoise strength.
    """
    try:
        lod_profile = get_lod_profile(req.lod)

        negative_prompt = req.negative_prompt or ", ".join(lod_profile.get("negatives", []))

        denoise_strength = req.denoise
        if denoise_strength is None:
            denoise_range = lod_profile.get("denoise_range", (0.3, 0.5))
            denoise_strength = (denoise_range[0] + denoise_range[1]) / 2

        result = generate_img2img(
            prompt=req.prompt,
            negative_prompt=negative_prompt,
            init_image_path=req.image_url,
            denoise_strength=denoise_strength,
            lod=req.lod,
            seed=req.seed,
            steps=req.steps,
            cfg=req.cfg,
            resolution=None,
            sampler=req.sampler,
            model_name=req.model,
        )

        response = {
            "scene_id": req.scene_id,
            "lod": req.lod,
            "image_url": result["image_url"],
            "model": result["model"],
            "seed": result["seed"],
            "steps": result["steps"],
            "cfg": result["cfg"],
            "denoise": result["denoise"],
            "duration_ms": result["duration_ms"],
        }

        LOG.info(
            f"Generated img2img via ComfyUI: {result['image_url']} "
            f"(seed={result['seed']}, denoise={result['denoise']})"
        )
        return response

    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(
            status_code=500,
            detail=f"Workflow or image not found: {e}",
        )
    except TimeoutError as e:
        raise HTTPException(status_code=504, detail=str(e))
    except Exception as e:
        LOG.exception("Img2img generation failed")
        raise HTTPException(status_code=500, detail=f"Img2img generation failed: {str(e)}")


@app.post("/parse")
def parse(req: ParseRequest) -> Dict[str, Any]:
    """
    Stub endpoint for parsing scenes from text.
    Returns empty list as this is handled by script-processor service.
    """
    return {"scenes": []}


@app.get("/images/{filename}")
def get_image(filename: str):
    """Serve generated image file."""
    image_path = STORAGE_DIR / filename
    if not image_path.exists():
        raise HTTPException(status_code=404, detail="Image not found")
    return FileResponse(image_path)
