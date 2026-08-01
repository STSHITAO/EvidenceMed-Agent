from __future__ import annotations

import io
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List

import fitz
import pdfplumber
from pypdf import PdfReader
from PIL import Image

from medical_rag.schemas import DocChunk

TEXT_SUFFIXES = {".md", ".txt", ".pdf"}
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".bmp", ".webp", ".tif", ".tiff"}

OCR_TEXT_THRESHOLD = 40
HEADER_FOOTER_SCAN_LINES = 3
HEADER_FOOTER_REPEAT_MIN = 2
MIN_BLOCK_LENGTH = 5
TITLE_PATTERNS = [
    re.compile(r"^\u7b2c[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u53430-9]+[\u7ae0\u8282\u90e8\u5206\u7bc7\u6761]"),
    re.compile(r"^\d+(\.\d+){0,3}\s+\S+"),
    re.compile(r"^[\uff08(][\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u53410-9]+[)\uff09]"),
    re.compile(r"^(\u9644\u5f55|\u53c2\u8003\u6587\u732e|\u6307\u5357\u5efa\u8bae|\u63a8\u8350\u610f\u89c1)"),
]


@dataclass
class TextBlock:
    text: str
    page: int
    kind: str = "paragraph"


def _read_text_file(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def _normalize_whitespace(text: str) -> str:
    lines = [line.strip() for line in text.replace("\u3000", " ").splitlines()]
    lines = [line for line in lines if line]
    return "\n".join(lines)


def _is_page_number(text: str) -> bool:
    cleaned = text.strip()
    if not cleaned:
        return False
    if cleaned.isdigit():
        return True
    lowered = cleaned.lower()
    return bool(
        re.fullmatch(r"(page\s*)?\d+", lowered)
        or re.fullmatch(r"\u7b2c\s*\d+\s*\u9875", cleaned)
    )


def _is_title(text: str) -> bool:
    stripped = text.strip()
    if len(stripped) > 80:
        return False
    return any(pattern.match(stripped) for pattern in TITLE_PATTERNS)


def _extract_table_blocks(path: Path) -> List[TextBlock]:
    table_blocks: List[TextBlock] = []
    with pdfplumber.open(str(path)) as pdf:
        for page_index, page in enumerate(pdf.pages, start=1):
            try:
                tables = page.extract_tables() or []
            except Exception:
                tables = []
            for table_index, table in enumerate(tables, start=1):
                if not table:
                    continue
                rows = []
                for row in table:
                    cleaned = [str(cell or "").strip() for cell in row]
                    if any(cleaned):
                        rows.append(" | ".join(cleaned))
                if rows:
                    text = f"Table(page={page_index}, index={table_index})\n" + "\n".join(rows)
                    table_blocks.append(TextBlock(text=text, page=page_index, kind="table"))
    return table_blocks


def _load_paddle_ocr():
    try:
        from paddleocr import PaddleOCR
    except ImportError as exc:  # pragma: no cover - runtime dependency
        raise RuntimeError(
            "PaddleOCR is required for scanned PDF OCR. Install `paddleocr` and a compatible `paddlepaddle` package."
        ) from exc
    return PaddleOCR(use_angle_cls=True, lang="ch")


def _render_page_to_pil(page: fitz.Page) -> Image.Image:
    pix = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
    return Image.open(io.BytesIO(pix.tobytes("png"))).convert("RGB")


def _ocr_page_text(page: fitz.Page, ocr_engine) -> str:
    image = _render_page_to_pil(page)
    result = ocr_engine.ocr(image, cls=True)
    lines: List[str] = []
    for item in result or []:
        for line in item or []:
            if len(line) >= 2 and isinstance(line[1], (list, tuple)):
                text = str(line[1][0]).strip()
                if text:
                    lines.append(text)
    return "\n".join(lines)


def _extract_pdf_blocks(path: Path) -> List[TextBlock]:
    doc = fitz.open(str(path))
    reader = PdfReader(str(path))
    ocr_engine = None
    blocks: List[TextBlock] = []

    for page_index, page in enumerate(doc, start=1):
        page_text = ""
        if page_index - 1 < len(reader.pages):
            try:
                page_text = reader.pages[page_index - 1].extract_text() or ""
            except Exception:
                page_text = ""

        if len(_normalize_whitespace(page_text)) < OCR_TEXT_THRESHOLD:
            if ocr_engine is None:
                ocr_engine = _load_paddle_ocr()
            page_text = _ocr_page_text(page, ocr_engine)
            normalized = _normalize_whitespace(page_text)
            if normalized:
                blocks.append(TextBlock(text=normalized, page=page_index, kind="ocr"))
            continue

        page_blocks = page.get_text("blocks") or []
        extracted_any = False
        sortable = []
        for block in page_blocks:
            if len(block) < 5:
                continue
            x0, y0, x1, y1, text = block[:5]
            cleaned = _normalize_whitespace(str(text))
            if len(cleaned) < MIN_BLOCK_LENGTH:
                continue
            sortable.append((round(y0, 1), round(x0, 1), cleaned))
        for _, _, cleaned in sorted(sortable, key=lambda item: (item[0], item[1])):
            extracted_any = True
            kind = "title" if _is_title(cleaned) else "paragraph"
            blocks.append(TextBlock(text=cleaned, page=page_index, kind=kind))

        if not extracted_any:
            normalized = _normalize_whitespace(page_text)
            if normalized:
                blocks.append(TextBlock(text=normalized, page=page_index, kind="paragraph"))

    table_blocks = _extract_table_blocks(path)
    blocks.extend(table_blocks)
    return blocks


def _remove_repeated_header_footer(blocks: List[TextBlock]) -> List[TextBlock]:
    page_to_blocks: dict[int, List[TextBlock]] = {}
    for block in blocks:
        page_to_blocks.setdefault(block.page, []).append(block)

    repeated_counter: Counter[str] = Counter()
    for page_blocks in page_to_blocks.values():
        if not page_blocks:
            continue
        ordered = page_blocks[:]
        top = ordered[:HEADER_FOOTER_SCAN_LINES]
        bottom = ordered[-HEADER_FOOTER_SCAN_LINES:]
        for block in top + bottom:
            text = block.text.strip()
            if len(text) <= 80:
                repeated_counter[text] += 1

    repeated_texts = {
        text
        for text, count in repeated_counter.items()
        if count >= HEADER_FOOTER_REPEAT_MIN or _is_page_number(text)
    }

    cleaned: List[TextBlock] = []
    for block in blocks:
        text = block.text.strip()
        if text in repeated_texts or _is_page_number(text):
            continue
        cleaned.append(block)
    return cleaned


def _merge_blocks_into_sections(blocks: List[TextBlock]) -> List[str]:
    sections: List[str] = []
    current: List[str] = []

    for block in blocks:
        text = block.text.strip()
        if not text:
            continue

        if block.kind in {"title", "table"}:
            if current:
                sections.append("\n".join(current).strip())
                current = []
            current.append(text)
            continue

        if current and _is_title(text):
            sections.append("\n".join(current).strip())
            current = [text]
            continue

        current.append(text)

    if current:
        sections.append("\n".join(current).strip())
    return [section for section in sections if section]


def _read_pdf_file(path: Path) -> str:
    blocks = _extract_pdf_blocks(path)
    blocks = _remove_repeated_header_footer(blocks)
    sections = _merge_blocks_into_sections(blocks)
    return "\n\n".join(sections)


def load_document_text(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix in {".md", ".txt"}:
        return _normalize_whitespace(_read_text_file(path))
    if suffix == ".pdf":
        return _read_pdf_file(path)
    raise ValueError(f"Unsupported document type: {suffix}")


def _split_long_section(section: str, chunk_size: int, chunk_overlap: int) -> List[str]:
    if len(section) <= chunk_size:
        return [section]

    step = max(1, chunk_size - chunk_overlap)
    chunks: List[str] = []
    start = 0
    while start < len(section):
        end = start + chunk_size
        chunks.append(section[start:end].strip())
        if end >= len(section):
            break
        start += step
    return [chunk for chunk in chunks if chunk]


def sliding_window_chunks(text: str, chunk_size: int, chunk_overlap: int) -> List[str]:
    normalized = _normalize_whitespace(text)
    if not normalized:
        return []

    raw_sections = [section.strip() for section in normalized.split("\n\n") if section.strip()]
    if not raw_sections:
        raw_sections = [normalized]

    chunks: List[str] = []
    buffer = ""

    def flush_buffer() -> None:
        nonlocal buffer
        if buffer.strip():
            chunks.append(buffer.strip())
            buffer = ""

    for section in raw_sections:
        if len(section) > chunk_size:
            flush_buffer()
            chunks.extend(_split_long_section(section, chunk_size=chunk_size, chunk_overlap=chunk_overlap))
            continue

        candidate = section if not buffer else f"{buffer}\n\n{section}"
        if len(candidate) <= chunk_size:
            buffer = candidate
        else:
            flush_buffer()
            buffer = section

    flush_buffer()
    return chunks


def build_chunks(doc_dir: str, chunk_size: int, chunk_overlap: int) -> List[DocChunk]:
    dir_path = Path(doc_dir)
    paths: Iterable[Path] = sorted(
        p for p in dir_path.rglob("*") if p.is_file() and p.suffix.lower() in TEXT_SUFFIXES
    )

    all_chunks: List[DocChunk] = []
    for path in paths:
        text = load_document_text(path)
        split_chunks = sliding_window_chunks(text, chunk_size=chunk_size, chunk_overlap=chunk_overlap)
        for idx, chunk in enumerate(split_chunks):
            all_chunks.append(
                DocChunk(
                    doc_id=f"{path.stem}-{idx}",
                    source=str(path),
                    chunk_index=idx,
                    content=chunk,
                    modality="text",
                    asset_path="",
                )
            )
    return all_chunks


def _load_sidecar_text(path: Path) -> str:
    for suffix in (".txt", ".md"):
        sidecar = path.with_suffix(suffix)
        if sidecar.exists():
            return _normalize_whitespace(sidecar.read_text(encoding="utf-8", errors="ignore"))
    return ""


def _fallback_image_description(path: Path) -> str:
    stem = path.stem.replace("_", " ").replace("-", " ").strip()
    parent = path.parent.name.replace("_", " ").replace("-", " ").strip()
    parts = [part for part in [parent, stem] if part]
    joined = " | ".join(parts) if parts else path.name
    return f"Medical reference image. {joined}"


def build_image_items(image_dir: str) -> List[DocChunk]:
    if not image_dir:
        return []

    dir_path = Path(image_dir)
    if not dir_path.exists():
        return []

    paths: Iterable[Path] = sorted(
        p for p in dir_path.rglob("*") if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES
    )

    items: List[DocChunk] = []
    for path in paths:
        description = _load_sidecar_text(path) or _fallback_image_description(path)
        items.append(
            DocChunk(
                doc_id=f"{path.stem}-image",
                source=str(path),
                chunk_index=0,
                content=description,
                modality="image",
                asset_path=str(path),
                metadata={"kind": "reference_image"},
            )
        )
    return items


def build_knowledge_items(doc_dir: str, image_dir: str, chunk_size: int, chunk_overlap: int) -> List[DocChunk]:
    text_items = build_chunks(doc_dir=doc_dir, chunk_size=chunk_size, chunk_overlap=chunk_overlap)
    resolved_image_dir = image_dir or str(Path(doc_dir) / "images")
    image_items = build_image_items(resolved_image_dir)
    return text_items + image_items
