# scene_splitter.py
"""
scene_splitter.py
-----------------
Модуль для разделения сценария на сцены.

Теперь поддерживает два режима:
1. Текстовый (для PDF, TXT и неформатированных DOCX)
2. Структурный (для DOCX, распознанных через scene_parser.py)

Если входные данные — список dict, то это уже структурированные сцены.
Если строка — используется регулярное выражение для поиска слаглайнов.

"""

import re
import logging
from typing import List, Union, Dict, Any

# --- Импортируем парсер структурных сцен ---
try:
    from scene_parser import parse_docx_structure
except ImportError:
    parse_docx_structure = None


# --- Регулярка для поиска слаглайнов ---
SCENE_KEYWORDS = r'(ИНТ\.?|НАТ\.?|ИНТ\.?/НАТ\.?|НАТ\.?/ИНТ\.?|ЭКСТ\.?|EXT\.?|INT\.?|NAT\.?)'
logger = logging.getLogger("scene_splitter")


def clean_text(text: str) -> str:
    """
    Очищает текст сценария от невидимых символов и нормализует переводы строк.
    """
    text = re.sub(r'[\u200B-\u200F\u2028-\u202F\u2060-\u206F\uFEFF]', '', text)
    text = text.replace('\xa0', ' ')
    text = text.replace('\t', ' ')
    text = text.replace('\r\n', '\n').replace('\r', '\n')
    text = re.sub(r'\n\s*\n+', '\n\n', text)
    text = '\n'.join(line.strip() for line in text.split('\n'))
    return text.strip()


def split_scenes(data: Union[str, List[Dict[str, Any]]]) -> List[Dict[str, Any]]:
    """
    Делит сценарий на сцены.

    Параметры:
        data: либо строка с полным текстом сценария,
              либо уже готовый список сцен (если docx структурный).

    Возвращает:
        Список сцен в формате:
        [
          {
            "slugline": "...",
            "blocks": [
                {"type": "action", "text": "..."},
                {"type": "dialogue_block", "character": "...", "text": "..."}
            ]
          },
          ...
        ]
    """

    # Если уже структурированный список сцен
    if isinstance(data, list) and data and isinstance(data[0], dict):
        return data

    # Если подали путь к файлу DOCX, можно попробовать парсер
    if isinstance(data, str) and data.lower().endswith(".docx") and parse_docx_structure:
        try:
            logger.info("Using structural DOCX parser for %s", data)
            return parse_docx_structure(data)
        except Exception as e:
            logger.warning("Structural DOCX parsing failed for %s: %s", data, e)

    # --- Текстовый режим ---
    text = clean_text(data)
    header_re = re.compile(rf'(?<!\w){SCENE_KEYWORDS}')

    scenes: List[Dict[str, Any]] = []
    current_lines: list[str] = []
    current_slug: str | None = None
    started = False

    for line in text.split('\n'):
        if header_re.search(line):
            # новая сцена
            if current_lines:
                scene_text = '\n'.join(current_lines).strip()
                if scene_text:
                    scenes.append({
                        "slugline": current_slug or "UNKNOWN",
                        "blocks": [{"type": "raw_text", "text": scene_text}]
                    })
            current_slug = line.strip()
            current_lines = [line]
            started = True
        else:
            if started:
                current_lines.append(line)
            else:
                continue  # до первой сцены игнорируем

    if current_lines:
        scene_text = '\n'.join(current_lines).strip()
        if scene_text:
            scenes.append({
                "slugline": current_slug or "UNKNOWN",
                "blocks": [{"type": "raw_text", "text": scene_text}]
            })

    return scenes


# --- Пример теста ---
if __name__ == "__main__":
    sample_text = """
    ИНТ. КВАРТИРА — ДЕНЬ
    Марина ставит чайник.
    Звонок в дверь.

    ЭКСТ. ДВОР — ВЕЧЕР
    Марина выходит во двор. На улице идёт дождь.
    """

    result = split_scenes(sample_text)
    print(f"✅ Найдено сцен: {len(result)}")
    for s in result:
        print(f"\n=== {s['slugline']} ===")
        for b in s["blocks"]:
            print(f"{b['type']}: {b['text'][:80]}")
