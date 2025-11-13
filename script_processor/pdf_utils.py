import re
import logging
import pdfplumber
from typing import Optional

logger = logging.getLogger("pdf_utils")


def clean_text(text: str) -> str:
    """
    Удаляет невидимые Unicode-символы, лишние пробелы и нормализует переводы строк.
    """
    # zero-width, BOM, нестандартные разделители строк
    text = re.sub(r'[\u200B-\u200F\u2028-\u202F\u2060-\u206F\uFEFF]', '', text)
    text = text.replace('\xa0', ' ')
    text = text.replace('\t', ' ')
    text = text.replace('\r\n', '\n').replace('\r', '\n')
    text = re.sub(r'\n\s*\n+', '\n\n', text)  # схлопываем много пустых строк
    text = re.sub(r'[ ]{2,}', ' ', text)
    return text.strip()


def fix_encoding(text: str) -> str:
    """
    Автоматически исправляет битую кириллическую кодировку:
    поддерживает utf-8, mac_cyrillic, cp1251.
    """
    candidates = ["utf-8", "mac_cyrillic", "cp1251"]
    for enc in candidates:
        try:
            fixed = text.encode("latin1").decode(enc)
            # если после перекодировки стало больше кириллических букв — считаем успешным
            if sum('А' <= c <= 'я' for c in fixed) > 10:
                return fixed
        except Exception:
            continue
    return text  # если не удалось — возвращаем исходное


def extract_text(pdf_path: str) -> str:
    """
    Извлекает текст из PDF и автоматически чинит кодировку.
    Возвращает очищенный и читаемый текст.
    """
    if not pdf_path.lower().endswith(".pdf"):
        raise ValueError("Файл должен иметь расширение .pdf")

    text_parts = []

    try:
        with pdfplumber.open(pdf_path) as pdf:
            for page_num, page in enumerate(pdf.pages, start=1):
                raw_text: Optional[str] = page.extract_text()
                if raw_text:
                    text_parts.append(raw_text)
                else:
                    logger.warning("PDF page %s is empty or unreadable", page_num)
    except Exception as e:
        raise RuntimeError(f"Ошибка при извлечении текста из PDF: {e}")

    # объединяем и пробуем исправить кодировку
    text = "\n".join(text_parts)
    text = fix_encoding(text)
    text = clean_text(text)

    return text


# тест запуска напрямую
if __name__ == "__main__":
    import sys
    if len(sys.argv) < 2:
        print("Использование: python pdf_utils.py path/to/script.pdf")
        sys.exit(1)

    path = sys.argv[1]
    txt = extract_text(path)
    print(f"✅ Извлечено {len(txt)} символов")
    print("Первые 500 символов:\n")
    print(txt[:500])
