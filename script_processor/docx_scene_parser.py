"""
docx_scene_parser.py
--------------------
Парсер DOCX-сценария по отступам и базовым правилам сценарного форматирования.

Что делает:
- Извлекает абзацы, читает их отступы и доп. признаки (CAPS, скобки).
- Классифицирует абзацы как: slugline | action | character | parenthetical | dialogue | transition | empty.
- Группирует в сцены (новая сцена начинается на slugline).

Зависимости:
- python-docx
"""

from typing import List, Dict, Any, Optional
import re

try:
    from docx import Document
    from docx.enum.text import WD_ALIGN_PARAGRAPH
except Exception:  # Лёгкая деградация, чтобы импорт модуля не падал без python-docx
    Document = None
    WD_ALIGN_PARAGRAPH = type("WD_ALIGN_PARAGRAPH", (), {"LEFT": 0, "CENTER": 1, "RIGHT": 2, "JUSTIFY": 3})


# --- Вспомогательные функции ---
def _norm_whitespace(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def _is_all_caps(text: str) -> bool:
    core = re.sub(r"[^A-Za-zА-Яа-яЁё0-9]+", "", text or "")
    return bool(core) and core.upper() == core


def _length_to_cm(length) -> float:
    # python-docx возвращает объект Length; у него есть .cm
    return float(getattr(length, "cm", 0.0)) if length is not None else 0.0


def _effective_left_indent_cm(p) -> float:
    # Берём локальный отступ абзаца; если None — от стиля; иначе 0
    pf = getattr(p, "paragraph_format", None)
    li = getattr(pf, "left_indent", None) if pf is not None else None
    if li is not None:
        return _length_to_cm(li)

    style = getattr(p, "style", None)
    spf = getattr(style, "paragraph_format", None) if style is not None else None
    li2 = getattr(spf, "left_indent", None) if spf is not None else None
    if li2 is not None:
        return _length_to_cm(li2)

    return 0.0


# Слаглайн: допускаем префикс нумерации сцены (например, "1-1.") и варианты через "/"
# Примеры:
#  - "1-1.ИНТ./НАТ. ШКОЛА №6. ЛЕСТНИЦА. НОЧЬ."
#  - "ИНТ. КВАРТИРА — ДЕНЬ"
SLUG_RE = re.compile(
    r"^(?:"
    r"(?:\d+)(?:\s*[-–]\s*[A-Za-zА-Яа-я0-9]+)*"  # поддержка "1-1", "1-6-А", "12-А-3" и т.п.
    r"\s*\.\s*"                                   # завершающая точка после префикса
    r")?"
    r"(?:"
    r"(?:ИНТ\.?|НАТ\.?|ЭКСТ\.?|INT\.?|EXT\.?)"
    r"(?:\s*/\s*(?:ИНТ\.?|НАТ\.?|INT\.?|EXT\.?))?"  # допускаем комбинированные INT/EXT и ИНТ./НАТ.
    r")"
    r"[\s\.\-—/]+.+?(?:ДЕНЬ|НОЧЬ|УТРО|ВЕЧЕР|СУМЕРКИ)\.?$",
    re.IGNORECASE,
)


def _classify_paragraph(p) -> str:
    text = _norm_whitespace(getattr(p, "text", ""))
    if not text:
        return "empty"

    # Выравнивание по умолчанию считаем левым
    align = getattr(p, "alignment", WD_ALIGN_PARAGRAPH.LEFT) or WD_ALIGN_PARAGRAPH.LEFT
    indent_cm = _effective_left_indent_cm(p)

    # 1) Слаглайн: левый столбец и шаблон
    if SLUG_RE.match(text) and align in (WD_ALIGN_PARAGRAPH.LEFT, WD_ALIGN_PARAGRAPH.JUSTIFY):
        return "slugline"

    # 2) Переход: часто правый и CAPS, заканчивается ":"
    if (align == WD_ALIGN_PARAGRAPH.RIGHT or text.endswith(":")) and _is_all_caps(text):
        return "transition"

    # 3) Action: отступ ~ 0 см (порог подстраиваемый)
    if indent_cm <= 0.05:
        return "action"

    # 4) Диалогная зона (отступ > 0): персонаж / ремарка / реплика
    if _is_all_caps(text) and len(text.split()) <= 4:
        return "character"
    if text.startswith("(") and text.endswith(")") and len(text) <= 120:
        return "parenthetical"
    return "dialogue"


# --- Выделение места/времени из слаглайна ---
TIME_WORDS = r"(ДЕНЬ|НОЧЬ|ВЕЧЕР|УТРО|СУМЕРКИ)"


def extract_time_from_slug(slug: str) -> Optional[str]:
    m = re.search(TIME_WORDS, (slug or "").upper())
    return m.group(1).title() if m else None


def extract_place_from_slug(slug: str) -> Optional[str]:
    s = re.sub(
        r"^(ИНТ\.?|НАТ\.?|ЭКСТ\.?|ИНТ\./НАТ\.?|НАТ\./ИНТ\.?|INT\.?|EXT\.?)\s*[\-—/]?\s*",
        "",
        slug or "",
        flags=re.IGNORECASE,
    )
    s = re.sub(TIME_WORDS + r".*$", "", s, flags=re.IGNORECASE).strip(" -—.")
    return s or None


# --- Основные API ---
def parse_docx_blocks_by_indent(path: str) -> List[Dict[str, Any]]:
    """
    Возвращает список абзацев с типами и текстом.
    type: slugline | action | character | parenthetical | dialogue | transition | empty
    """
    if Document is None:
        raise ImportError("python-docx не установлен. Установите пакет: pip install python-docx")

    doc = Document(path)
    blocks: List[Dict[str, Any]] = []

    for p in doc.paragraphs:
        kind = _classify_paragraph(p)
        text = _norm_whitespace(getattr(p, "text", ""))
        if kind == "empty":
            continue
        indent_cm = _effective_left_indent_cm(p)
        align = getattr(p, "alignment", WD_ALIGN_PARAGRAPH.LEFT) or WD_ALIGN_PARAGRAPH.LEFT
        blocks.append({"type": kind, "text": text, "indent_cm": indent_cm, "align": int(align)})

    return blocks


def parse_docx_to_scenes(path: str) -> List[Dict[str, Any]]:
    """
    Группирует блоки в сцены; новая сцена начинается на slugline.
    Возвращает список сцен вида:
    {
      "slugline": str,
      "place": Optional[str],
      "time": Optional[str],
      "blocks": [
         {"type": "action", "text": str} |
         {"type": "dialogue", "character": str, "parenthetical": Optional[str], "text": str} |
         {"type": "transition", "text": str}
      ]
    }
    """
    blocks = parse_docx_blocks_by_indent(path)

    scenes: List[Dict[str, Any]] = []
    current_scene: Optional[Dict[str, Any]] = None
    pending_character: Optional[str] = None
    pending_parenthetical: Optional[str] = None

    def _start_scene(slug: str) -> Dict[str, Any]:
        return {
            "slugline": slug,
            "place": extract_place_from_slug(slug),
            "time": extract_time_from_slug(slug),
            "blocks": [],
        }

    for b in blocks:
        kind = b["type"]
        text = b["text"]

        if kind == "slugline":
            if current_scene:
                scenes.append(current_scene)
            current_scene = _start_scene(text)
            pending_character = None
            pending_parenthetical = None
            continue

        if current_scene is None:
            # Игнорируем всё до первого слаглайна
            continue

        if kind == "action":
            current_scene["blocks"].append({
                "type": "action",
                "text": text,
                "indent_cm": b.get("indent_cm", 0.0),
                "align": b.get("align"),
            })
            pending_character = None
            pending_parenthetical = None
        elif kind == "character":
            pending_character = {
                "text": text,
                "indent_cm": b.get("indent_cm", 0.0),
                "align": b.get("align"),
            }
            pending_parenthetical = None
        elif kind == "parenthetical":
            pending_parenthetical = {
                "text": text,
                "indent_cm": b.get("indent_cm", 0.0),
                "align": b.get("align"),
            }
        elif kind == "dialogue":
            if pending_character:
                current_scene["blocks"].append({
                    "type": "dialogue",
                    "character": pending_character.get("text"),
                    "character_indent_cm": pending_character.get("indent_cm", 0.0),
                    "parenthetical": pending_parenthetical and pending_parenthetical.get("text"),
                    "parenthetical_indent_cm": pending_parenthetical and pending_parenthetical.get("indent_cm", 0.0),
                    "text": text,
                    "dialogue_indent_cm": b.get("indent_cm", 0.0),
                })
                pending_parenthetical = None
            else:
                # На всякий случай: реплика без имени — трактуем как action
                current_scene["blocks"].append({
                    "type": "action",
                    "text": text,
                    "indent_cm": b.get("indent_cm", 0.0),
                    "align": b.get("align"),
                })
        elif kind == "transition":
            current_scene["blocks"].append({"type": "transition", "text": text})
            pending_character = None
            pending_parenthetical = None

    if current_scene:
        scenes.append(current_scene)

    return scenes


def render_scene_to_text(scene: Dict[str, Any]) -> str:
    """
    Сериализует сцену в плоский текст для последующей токенизации/чанкинга.
    Сохраняем логическую структуру, добавляя понятные маркеры.
    """
    lines: List[str] = []
    lines.append(scene.get("slugline", "").strip())

    for b in scene.get("blocks", []):
        if b["type"] == "action":
            lines.append(b["text"])
        elif b["type"] == "dialogue":
            who = b.get("character", "").strip()
            parenth = b.get("parenthetical")
            if who:
                lines.append(who)
            if parenth:
                lines.append(parenth)
            lines.append(b.get("text", ""))
        elif b["type"] == "transition":
            lines.append(b["text"])

    # Две пустые строки между сценами позже обеспечит token_chunker._join_scenes_for_chunk
    return "\n".join([s for s in lines if s])


def parse_docx_to_scene_texts(path: str) -> List[str]:
    """
    Удобный фасад: получить список текстов сцен (List[str]) из DOCX.
    """
    scenes = parse_docx_to_scenes(path)
    return [render_scene_to_text(s) for s in scenes]


