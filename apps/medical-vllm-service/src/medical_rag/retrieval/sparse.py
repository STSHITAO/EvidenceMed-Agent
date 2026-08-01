from __future__ import annotations

import json
import re
from dataclasses import asdict
from pathlib import Path
from typing import Iterable, List

from rank_bm25 import BM25Okapi

from medical_rag.config import SparseConfig
from medical_rag.schemas import DocChunk, RetrievedChunk

_LATIN_TOKEN_RE = re.compile(r"[A-Za-z0-9_]+")
_CJK_RE = re.compile(r"[\u4e00-\u9fff]")


def tokenize_sparse_text(text: str) -> List[str]:
    normalized = (text or "").lower()
    tokens: List[str] = _LATIN_TOKEN_RE.findall(normalized)
    tokens.extend(_CJK_RE.findall(normalized))
    if not tokens:
        tokens = list(normalized.strip())
    return [token for token in tokens if token.strip()]


def write_sparse_corpus(path: str, items: Iterable[DocChunk]) -> int:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with target.open("w", encoding="utf-8") as f:
        for item in items:
            if not item.content.strip():
                continue
            payload = asdict(item)
            f.write(json.dumps(payload, ensure_ascii=False) + "\n")
            count += 1
    return count


class SparseRetriever:
    def __init__(self, config: SparseConfig) -> None:
        self.config = config
        self.items: List[DocChunk] = []
        self.bm25: BM25Okapi | None = None

    def ready(self) -> bool:
        return self.bm25 is not None

    def load(self) -> None:
        corpus_path = Path(self.config.corpus_path)
        if not corpus_path.exists():
            self.items = []
            self.bm25 = None
            return

        items: List[DocChunk] = []
        tokenized_corpus: List[List[str]] = []

        with corpus_path.open("r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                raw = json.loads(line)
                item = DocChunk(
                    doc_id=raw["doc_id"],
                    source=raw["source"],
                    chunk_index=int(raw["chunk_index"]),
                    content=raw["content"],
                    modality=raw.get("modality", "text"),
                    asset_path=raw.get("asset_path", ""),
                    metadata=raw.get("metadata", {}) or {},
                )
                items.append(item)
                tokenized_corpus.append(tokenize_sparse_text(item.content))

        if not tokenized_corpus:
            self.items = []
            self.bm25 = None
            return

        self.items = items
        self.bm25 = BM25Okapi(tokenized_corpus, k1=self.config.bm25_k1, b=self.config.bm25_b)

    def search(self, query: str, top_k: int | None = None) -> List[RetrievedChunk]:
        if self.bm25 is None:
            return []

        limit = top_k or self.config.top_k
        tokens = tokenize_sparse_text(query)
        if not tokens:
            return []

        scores = self.bm25.get_scores(tokens)
        ranked_indices = sorted(range(len(scores)), key=lambda idx: scores[idx], reverse=True)

        results: List[RetrievedChunk] = []
        for idx in ranked_indices[:limit]:
            item = self.items[idx]
            score = float(scores[idx])
            if score <= 0:
                continue
            results.append(
                RetrievedChunk(
                    doc_id=item.doc_id,
                    source=item.source,
                    chunk_index=item.chunk_index,
                    content=item.content,
                    score=score,
                    modality=item.modality,
                    asset_path=item.asset_path,
                    metadata=item.metadata,
                )
            )
        return results
