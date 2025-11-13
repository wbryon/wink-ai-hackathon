# api.py
"""
api.py
------
FastAPI-сервис для обработки сценариев (PDF/DOCX):
- извлекает текст или структуру
- делит на сцены
- чанкит по количеству токенов

Поддержка:
- PDF (plain text)
- DOCX (форматированный сценарий)
"""

from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
import tempfile
import time
import os
from datetime import datetime
import logging

from pdf_utils import extract_text as extract_pdf_text
from docx_utils import extract_text as extract_docx_text, extract_script_structure
from scene_splitter import split_scenes
from token_chunker import get_tokenizer, chunk_scenes
try:
    from docx_scene_parser import parse_docx_to_scenes as parse_docx_by_indent
except Exception:
    parse_docx_by_indent = None
import re

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("script_processor.api")

SCENE_KEYWORDS = r'(ИНТ\.?|НАТ\.?|ИНТ\.?/НАТ\.?|НАТ\.?/ИНТ\.?|ЭКСТ\.?|EXT\.?|INT\.?|NAT\.?)'

def ensure_blank_line_before_sluglines(text: str) -> str:
    """
    Проходит по тексту чанка построчно и гарантирует,
    что перед каждой строкой-слаглайном есть ПУСТАЯ строка.
    """
    lines = text.splitlines()
    out = []
    for i, line in enumerate(lines):
        is_slug = bool(re.match(
            rf'^\s*(?:\d+[^\S\r\n]*[-–][^\S\r\n]*)?{SCENE_KEYWORDS}\b', line, flags=re.IGNORECASE))
        if is_slug and out and out[-1].strip() != '':
            out.append('')  # вставляем пустую строку
        out.append(line)
    return '\n'.join(out).rstrip() + '\n'

# --- Инициализация приложения ---
app = FastAPI(
    title="Script Chunking API",
    description="API для разбиения сценариев (PDF/DOCX) на сцены и чанки по токенам.",
    version="1.4.1",
)

BASE_CHUNKS_DIR = "chunks"
os.makedirs(BASE_CHUNKS_DIR, exist_ok=True)


# --- Основной эндпоинт ---
@app.post("/split-script")
async def split_script(
    file: UploadFile = File(..., description="PDF или DOCX файл сценария"),
    model: str = "Qwen/Qwen3-32B",
    parse_mode: str = "auto"  # auto | indent | legacy | plain
):
    """
    Принимает PDF или DOCX, извлекает текст или структуру,
    делит на сцены и чанки по токенам.
    Для каждого вызова создаётся уникальная подпапка в 'chunks/'.
    После обработки временный файл удаляется.
    """
    start_time = time.time()
    ext = os.path.splitext(file.filename.lower())[1]

    if ext not in [".pdf", ".docx"]:
        raise HTTPException(status_code=400, detail="Поддерживаются только PDF и DOCX файлы")

    # --- Создаём уникальную подпапку для результата ---
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    safe_name = os.path.splitext(os.path.basename(file.filename))[0]
    session_dir = os.path.join(BASE_CHUNKS_DIR, f"{timestamp}_{safe_name}")
    os.makedirs(session_dir, exist_ok=True)

    logger.info("New processing session: %s", session_dir)

    tmp_path = None
    try:
        # --- Сохраняем временный файл ---
        with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
            tmp.write(await file.read())
            tmp_path = tmp.name

        logger.debug("Temporary upload saved to %s", tmp_path)

        # --- Извлечение текста / структуры ---
        scenes = None
        if ext == ".pdf":
            text = extract_pdf_text(tmp_path)
            if not text.strip():
                raise HTTPException(status_code=422, detail="Не удалось извлечь текст из PDF")
            scenes = split_scenes(text)
        else:
            # DOCX: в зависимости от режима
            mode = (parse_mode or "auto").lower()
            if mode not in ("auto", "indent", "legacy", "plain"):
                raise HTTPException(status_code=400, detail="parse_mode должен быть одним из: auto | indent | legacy | plain")

            def _by_indent():
                if parse_docx_by_indent is None:
                    raise RuntimeError("docx_scene_parser недоступен")
                sc = parse_docx_by_indent(tmp_path)
                if not sc:
                    raise ValueError("parse_docx_by_indent вернул пусто")
                logger.info("DOCX parsed by indentation (%d scenes)", len(sc))
                return sc

            def _by_legacy():
                sc = extract_script_structure(tmp_path)
                if not sc:
                    raise ValueError("структурный парсер вернул пусто")
                logger.info("Legacy structural parser succeeded (%d scenes)", len(sc))
                return sc

            def _by_plain():
                txt = extract_docx_text(tmp_path)
                if not txt.strip():
                    raise HTTPException(status_code=422, detail="Не удалось извлечь текст из DOCX")
                sc = split_scenes(txt)
                logger.info("DOCX treated as plain text (%d scenes)", len(sc))
                return sc

            if mode == "indent":
                scenes = _by_indent()
            elif mode == "legacy":
                scenes = _by_legacy()
            elif mode == "plain":
                scenes = _by_plain()
            else:
                # auto: пытаемся по порядку
                try:
                    scenes = _by_indent()
                except Exception as e_primary:
                    logger.warning("parse_docx_by_indent failed: %s. Trying legacy parser.", e_primary)
                    try:
                        scenes = _by_legacy()
                    except Exception as e_secondary:
                        logger.warning("Legacy parser failed (%s). Fallback to plain text.", e_secondary)
                        scenes = _by_plain()

        if not scenes:
            raise HTTPException(status_code=422, detail="Не удалось выделить сцены")

        logger.info("Scenes extracted: %d", len(scenes))

        # --- Загружаем токенайзер ---
        tokenizer = get_tokenizer(model)

        # --- Формируем текстовые представления сцен ---
        scene_texts = []
        def cm_to_spaces(cm: float, factor: float = 4.0) -> int:
            try:
                return max(0, int(round((cm or 0.0) * factor)))
            except Exception:
                return 0

        for scene in scenes:
            slug = scene.get("slugline") or "UNKNOWN"
            block_texts = []
            for b in scene.get("blocks", []):
                btype = b.get("type")
                if btype in ("dialogue_block", "dialogue"):
                    character = b.get("character", "UNKNOWN").strip()
                    text = b.get("text", "").strip()
                    parenthetical = b.get("parenthetical")

                    # Используем реальные отступы из DOCX, если есть
                    c_indent = cm_to_spaces(b.get("character_indent_cm"))
                    d_indent = cm_to_spaces(b.get("dialogue_indent_cm"))
                    p_indent = cm_to_spaces(b.get("parenthetical_indent_cm")) if parenthetical else None

                    if character:
                        block_texts.append((" " * c_indent) + character)
                    if parenthetical:
                        block_texts.append((" " * (p_indent or d_indent)) + parenthetical)
                    if text:
                        block_texts.append((" " * d_indent) + text)
                else:
                    text = b.get("text", "").strip()
                    if text:
                        # для action можем тоже учесть отступ, если он есть
                        a_indent = b.get("indent_cm")
                        if a_indent is not None:
                            block_texts.append((" " * cm_to_spaces(a_indent)) + text)
                        else:
                            block_texts.append(text)
            # Без префикса: начинаем сцену сразу с слаглайна, затем пустая строка
            full_scene_text = slug + "\n\n" + "\n".join(t for t in block_texts if t) + "\n"
            scene_texts.append(full_scene_text)
        if scene_texts:
            logger.debug("First scene sample:\n%s", scene_texts[0][:400])

        # --- Формируем чанки ---
        chunks = chunk_scenes(scene_texts, tokenizer)
        logger.info("Chunks created: %d", len(chunks))

        # --- Сохраняем чанки ---
        saved_files = []
        response_chunks = []
        for chunk in chunks:
            filename = f"chunk_{chunk['chunk_index']:03d}.txt"
            file_path = os.path.join(session_dir, filename)

            # ✅ финальная нормализация: добавляем пустую строку перед каждым новым слаглайном
            normalized_text = ensure_blank_line_before_sluglines(chunk["text"])

            # на Windows можно зафиксировать перевод строк:
            with open(file_path, "w", encoding="utf-8", newline="\n") as f:
                f.write(normalized_text)

            saved_files.append(file_path)
            response_chunks.append({
                "chunk_index": chunk["chunk_index"],
                "token_count": chunk["token_count"],
                "text": normalized_text
            })

        elapsed = round(time.time() - start_time, 2)
        logger.info("Processing finished: %d chunks in %s seconds", len(chunks), elapsed)

        return JSONResponse({
            "filename": file.filename,
            "model": model,
            "total_scenes": len(scenes),
            "total_chunks": len(chunks),
            "processing_time_sec": elapsed,
            "chunks_dir": os.path.abspath(session_dir),
            "saved_files": saved_files,
            "chunks": response_chunks
        })

    except HTTPException:
        raise
    except Exception as e:
        logger.exception("Unhandled error during split_script: %s", e)
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        # --- Удаляем временный файл ---
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
                logger.debug("Temporary file removed: %s", tmp_path)
            except Exception as cleanup_err:
                logger.warning("Failed to remove temporary file %s: %s", tmp_path, cleanup_err)
