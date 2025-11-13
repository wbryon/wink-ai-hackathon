# scene_parser.py
from docx import Document
import re

def classify_paragraph(p):
    text = p.text.strip()
    if not text:
        return None
    align = p.alignment
    if re.match(r'^(ИНТ\.?|ЭКСТ\.?|INT\.?|EXT\.?)', text) and text.isupper():
        return "slugline"
    if align == 1 and text.isupper() and not text.startswith("("):
        return "character"
    if align == 1 and text.startswith("(") and text.endswith(")"):
        return "parenthetical"
    if align == 1 and not text.isupper():
        return "dialogue"
    if align == 2 and text.isupper():
        return "transition"
    return "action"


def parse_docx_structure(docx_path):
    """
    Возвращает список сцен со структурой:
    [
      {
        "slugline": "ИНТ. ШКОЛА — ДЕНЬ",
        "blocks": [
           {"type": "action", "text": "..."},
           {"type": "dialogue_block",
             "character": "БОРИС",
             "parenthetical": "(шёпотом)",
             "text": "Мирра, нам нужно уходить."}
        ]
      }, ...
    ]
    """
    doc = Document(docx_path)
    scenes = []
    current_scene = None
    current_dialogue = None

    for p in doc.paragraphs:
        kind = classify_paragraph(p)
        if not kind:
            continue
        text = p.text.strip()

        if kind == "slugline":
            if current_scene:
                scenes.append(current_scene)
            current_scene = {"slugline": text, "blocks": []}
            current_dialogue = None
            continue

        if not current_scene:
            current_scene = {"slugline": None, "blocks": []}

        if kind == "character":
            current_dialogue = {"type": "dialogue_block", "character": text, "parenthetical": None, "text": ""}
            current_scene["blocks"].append(current_dialogue)
        elif kind == "parenthetical" and current_dialogue:
            current_dialogue["parenthetical"] = text
        elif kind == "dialogue" and current_dialogue:
            current_dialogue["text"] += (" " + text)
        else:
            current_scene["blocks"].append({"type": kind, "text": text})
            current_dialogue = None

    if current_scene:
        scenes.append(current_scene)
    return scenes
