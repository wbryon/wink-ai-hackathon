# docx_utils.py
import re
import logging

from docx import Document
try:
    from scene_parser import parse_docx_structure
except Exception:
    parse_docx_structure = None

logger = logging.getLogger("docx_utils")

def extract_script_structure(docx_path: str):
    """
    Анализирует DOCX как сценарий и возвращает структурированные сцены.
    """
    if parse_docx_structure is None:
        msg = "scene_parser недоступен для структурного DOCX парсинга"
        logger.warning(msg)
        raise RuntimeError(msg)
    try:
        scenes = parse_docx_structure(docx_path)
        logger.info("Scene parser extracted %d scenes from %s", len(scenes), docx_path)
    except Exception as e:
        logger.error("Ошибка при анализе сценария DOCX %s: %s", docx_path, e)
        raise RuntimeError(f"Ошибка при анализе сценария DOCX: {e}")
    return scenes



def clean_text(text: str) -> str:
    """
    Удаляет невидимые Unicode-символы, лишние пробелы и нормализует переводы строк.
    """
    text = re.sub(r'[\u200B-\u200F\u2028-\u202F\u2060-\u206F\uFEFF]', '', text)
    text = text.replace('\xa0', ' ')
    text = text.replace('\t', ' ')
    text = text.replace('\r\n', '\n').replace('\r', '\n')
    text = re.sub(r'\n\s*\n+', '\n\n', text)
    text = re.sub(r'[ ]{2,}', ' ', text)
    return text.strip()


def extract_text(docx_path: str) -> str:
    """
    Извлекает текст из DOCX-файла.
    Возвращает очищенный текст.
    """
    if not docx_path.lower().endswith(".docx"):
        raise ValueError("Файл должен иметь расширение .docx")

    try:
        doc = Document(docx_path)
    except Exception as e:
        raise RuntimeError(f"Ошибка при открытии DOCX: {e}")

    paragraphs = []
    for para in doc.paragraphs:
        text = para.text.strip()
        if text:
            paragraphs.append(text)

    # Иногда сценарии в DOCX содержат разрывы страниц или двойные абзацы.
    text = "\n".join(paragraphs)
    text = clean_text(text)

    return text


# тестовый запуск напрямую
if __name__ == "__main__":
    import sys
    if len(sys.argv) < 2:
        print("Использование: python docx_utils.py path/to/script.docx")
        sys.exit(1)

    path = sys.argv[1]
    txt = extract_text(path)
    print(f"✅ Извлечено {len(txt)} символов")
    print("Первые 500 символов:\n")
    print(txt[:500])
