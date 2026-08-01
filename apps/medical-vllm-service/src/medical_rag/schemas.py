from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, List


@dataclass
class DocChunk:
    doc_id: str
    source: str
    chunk_index: int
    content: str
    modality: str = "text"
    asset_path: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class RetrievedChunk:
    doc_id: str
    source: str
    chunk_index: int
    content: str
    score: float
    modality: str = "text"
    asset_path: str = ""
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class EmbedInput:
    text: str = ""
    image_path: str = ""
    instruction: str = ""


@dataclass
class RagAnswer:
    answer: str
    evidence: List[RetrievedChunk]
    hyde_text: str = ""
