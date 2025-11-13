"""
token_chunker.py
----------------
Модуль для разбиения списка сцен сценария на чанки по количеству токенов.

Поддерживаемые модели:
- Qwen/Qwen3-32B
- mistralai/Mistral-7B-Instruct-v0.3

Логика:
- Целевой размер чанка: 4000 токенов (SOFT_LIMIT)
- Максимум: 4200 токенов (HARD_LIMIT)
- Если добавление сцены превышает лимит — создаётся новый чанк
- Если одна сцена > HARD_LIMIT — делится на подчанки (по абзацам или строкам)

Автор: (ваше имя)
Дата: 2025-11-11
"""

import re
import math
import logging
from typing import List, Dict, Any

# --- Константы лимитов ---
SOFT_LIMIT = 4000
HARD_LIMIT = 4200


# --- Инициализация токенайзера ---
from transformers import PreTrainedTokenizerFast
import os
from functools import lru_cache

try:
    from docx_scene_parser import parse_docx_to_scene_texts
except Exception:
    parse_docx_to_scene_texts = None
import argparse

logger = logging.getLogger("token_chunker")

def _normalize_newlines(text: str) -> str:
    """
    Приводит переводы строк к LF, чтобы корректно работать на Windows/Linux/Mac.
    """
    if not text:
        return ""
    # Сначала CRLF -> LF, затем одинокие CR -> LF (редкие случаи)
    return text.replace("\r\n", "\n").replace("\r", "\n")


def _join_scenes_for_chunk(parts: List[str]) -> str:
    """
    Склеивает сцены для одного чанка, гарантируя визуальное
    разделение сцен двумя пустыми строками даже на Windows (CRLF).
    """
    if not parts:
        return ""

    # Очищаем, нормализуем переводы строк и фильтруем пустые сцены
    cleaned = []
    for p in parts:
        if not p:
            continue
        normalized = _normalize_newlines(p).strip()
        if normalized:
            cleaned.append(normalized)

    if not cleaned:
        return ""

    # Три LF между сценами (даёт 2 пустых строки), в конце — одна пустая строка
    joined = ("\n\n\n").join(cleaned) + "\n\n"
    return joined


@lru_cache(maxsize=4)
def get_tokenizer(model_name: str):
    LOCAL_MODELS = {
        "Qwen/Qwen3-32B": "./models/Qwen3-32B",
        "mistralai/Mistral-7B-Instruct-v0.3": "./models/Mistral-7B"
    }

    model_path = LOCAL_MODELS.get(model_name)
    if not model_path or not os.path.exists(model_path):
        msg = f"Локальная модель не найдена: {model_name}. Ожидается {LOCAL_MODELS}"
        logger.error(msg)
        raise ValueError(msg)

    tokenizer_json = os.path.join(model_path, "tokenizer.json")
    if not os.path.exists(tokenizer_json):
        msg = f"Не найден файл {tokenizer_json}"
        logger.error(msg)
        raise FileNotFoundError(msg)

    try:
        tokenizer = PreTrainedTokenizerFast(tokenizer_file=tokenizer_json)
        _ = len(tokenizer.encode("test"))
        logger.info("Tokenizer %s загружен из %s", model_name, tokenizer_json)
        return tokenizer
    except Exception as e:
        logger.warning("Не удалось загрузить токенайзер из %s: %s. Используем эвристический fallback.", tokenizer_json, e)

        class FallbackTokenizer:
            def encode(self, text: str):
                if not text:
                    return []
                approx_by_chars = max(1, math.ceil(len(text) / 4))
                approx_by_words = len(re.findall(r"\S+", text))
                return [0] * max(approx_by_chars, approx_by_words)

        return FallbackTokenizer()


# --- Разделение слишком длинных сцен ---
def split_large_scene(scene: str, tokenizer) -> List[str]:
    """
    Делит сцену на подчанки, если она превышает HARD_LIMIT.

    Стратегия:
    1. Разделяем по абзацам (\n{2,})
    2. Если всё ещё слишком длинно — делим по строкам
    """

    # Нормализуем переводы строк, чтобы корректно разделять по пустым строкам и на Windows
    scene = _normalize_newlines(scene)

    # Разделяем по двум и более пустым строкам (LF-нормализованно)
    paras = re.split(r"\n{2,}", scene)
    subchunks, current, tokens = [], [], 0

    for para in paras:
        ptoks = len(tokenizer.encode(para))
        if ptoks > HARD_LIMIT:
            # Разбиваем по строкам
            lines = para.splitlines()
            temp, tcount = [], 0
            for line in lines:
                ltoks = len(tokenizer.encode(line))
                if tcount + ltoks <= HARD_LIMIT:
                    temp.append(line)
                    tcount += ltoks
                else:
                    if temp:
                        subchunks.append("\n".join(temp))
                    temp, tcount = [line], ltoks
            if temp:
                subchunks.append("\n".join(temp))
        else:
            if tokens + ptoks <= HARD_LIMIT:
                current.append(para)
                tokens += ptoks
            else:
                subchunks.append("\n\n".join(current))
                current, tokens = [para], ptoks

    if current:
        subchunks.append("\n\n".join(current))

    return subchunks


# --- Основная функция ---
def chunk_scenes(scenes: List[str], tokenizer) -> List[Dict[str, Any]]:
    """
    Разбивает список сцен на чанки по количеству токенов.

    Параметры:
        scenes (List[str]): список сцен
        tokenizer: токенайзер из transformers

    Возвращает:
        List[Dict]: список чанков с индексами, текстом и количеством токенов
    """

    chunks = []
    current, current_tokens = [], 0
    chunk_index = 1

    for scene in scenes:
        scene_tokens = len(tokenizer.encode(scene))

        # Если сцена слишком длинная — делим на подчанки
        if scene_tokens > HARD_LIMIT:
            for subscene in split_large_scene(scene, tokenizer):
                subtoks = len(tokenizer.encode(subscene))
                if current_tokens + subtoks <= HARD_LIMIT:
                    current.append(subscene)
                    current_tokens += subtoks
                else:
                    chunks.append({
                        "chunk_index": chunk_index,
                        "token_count": current_tokens,
                        "text": _join_scenes_for_chunk(current)
                    })
                    chunk_index += 1
                    current = [subscene]
                    current_tokens = subtoks
            continue

        # Если влезает в текущий чанк — добавляем
        if current_tokens + scene_tokens <= HARD_LIMIT:
            current.append(scene)
            current_tokens += scene_tokens
        else:
            # Закрываем чанк и начинаем новый
            chunks.append({
                "chunk_index": chunk_index,
                "token_count": current_tokens,
                "text": _join_scenes_for_chunk(current)
            })
            chunk_index += 1
            current = [scene]
            current_tokens = scene_tokens

    # Добавляем последний чанк
    if current:
        chunks.append({
            "chunk_index": chunk_index,
            "token_count": current_tokens,
            "text": _join_scenes_for_chunk(current)
        })

    return chunks


# --- Пример использования ---
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Разбиение сценария на чанки по токенам")
    parser.add_argument("--model", type=str, default="mistralai/Mistral-7B-Instruct-v0.3", help="Имя локальной модели")
    parser.add_argument("--docx", type=str, default=None, help="Путь к файлу сценария .docx")
    args = parser.parse_args()

    tokenizer = get_tokenizer(args.model)

    if args.docx and parse_docx_to_scene_texts is not None:
        scenes = parse_docx_to_scene_texts(args.docx)
    else:
        # Фолбэк: встроенный тест
        scenes = [
            "ИНТ. КВАРТИРА — ДЕНЬ\nМарина ставит чайник.\n— Привет!",
            "НАТ. УЛИЦА — ВЕЧЕР\nТолпа идёт по улице. Свет фонарей отражается на асфальте.",
            "ИНТ. КАФЕ — НОЧЬ\nБольшой монолог, очень длинный..." * 500
        ]

    chunks = chunk_scenes(scenes, tokenizer)

    print(f"Создано чанков: {len(chunks)}")
    for c in chunks:
        print(f"Чанк {c['chunk_index']}: {c['token_count']} токенов")
        print("-" * 60)
