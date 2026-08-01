from __future__ import annotations

from collections import defaultdict
from typing import Dict, List

from medical_rag.config import EmbeddingConfig
from medical_rag.retrieval.embedding import Embedder
from medical_rag.retrieval.sparse import SparseRetriever
from medical_rag.retrieval.vector_store import MilvusVectorStore
from medical_rag.schemas import EmbedInput, RetrievedChunk


class MultiRouteRetriever:
    """Multimodal multi-route recall + reciprocal rank fusion + optional HyDE branch."""

    def __init__(
        self,
        embedder: Embedder,
        store: MilvusVectorStore,
        embedding_config: EmbeddingConfig,
        sparse_retriever: SparseRetriever | None = None,
        route_top_k: int = 8,
    ) -> None:
        self.embedder = embedder
        self.store = store
        self.embedding_config = embedding_config
        self.sparse_retriever = sparse_retriever
        self.route_top_k = route_top_k

    @staticmethod
    def _expand_text_queries(query: str) -> List[str]:
        q = query.strip()
        return [
            q,
            f"影像征象 {q}",
            f"临床指南 {q}",
        ]

    def _build_routes(self, query: str, image_path: str = "", hyde_text: str = "") -> List[EmbedInput]:
        routes: List[EmbedInput] = []

        for text_query in self._expand_text_queries(query):
            routes.append(
                EmbedInput(
                    text=text_query,
                )
            )

        if image_path:
            routes.append(
                EmbedInput(
                    text=query,
                    image_path=image_path,
                    instruction=self.embedding_config.multimodal_query_instruction,
                )
            )
            routes.append(
                EmbedInput(
                    text="",
                    image_path=image_path,
                    instruction=self.embedding_config.multimodal_query_instruction,
                )
            )

        if hyde_text.strip():
            routes.append(
                EmbedInput(
                    text=hyde_text.strip(),
                    instruction=self.embedding_config.hyde_instruction,
                )
            )

        return routes

    @staticmethod
    def _build_sparse_queries(query: str, hyde_text: str = "") -> List[str]:
        queries = [query.strip(), f"影像征象 {query.strip()}", f"临床指南 {query.strip()}"]
        if hyde_text.strip():
            queries.append(hyde_text.strip())
        return [q for q in queries if q]

    def recall(self, query: str, top_k: int, image_path: str = "", hyde_text: str = "") -> List[RetrievedChunk]:
        routes = self._build_routes(query=query, image_path=image_path, hyde_text=hyde_text)
        route_vectors = self.embedder.encode(routes)

        rrf_k = 60
        fused_score: Dict[str, float] = defaultdict(float)
        hit_cache: Dict[str, RetrievedChunk] = {}

        for route_vec in route_vectors:
            route_hits = self.store.search(route_vec, top_k=self.route_top_k)
            for rank, hit in enumerate(route_hits, start=1):
                key = f"{hit.doc_id}::{hit.chunk_index}::{hit.modality}::{hit.asset_path}"
                fused_score[key] += 1.0 / (rrf_k + rank)
                if key not in hit_cache:
                    hit_cache[key] = hit

        if self.sparse_retriever is not None and self.sparse_retriever.ready():
            sparse_queries = self._build_sparse_queries(query=query, hyde_text=hyde_text)
            for sparse_query in sparse_queries:
                sparse_hits = self.sparse_retriever.search(sparse_query, top_k=self.route_top_k)
                for rank, hit in enumerate(sparse_hits, start=1):
                    key = f"{hit.doc_id}::{hit.chunk_index}::{hit.modality}::{hit.asset_path}"
                    fused_score[key] += 1.0 / (rrf_k + rank)
                    if key not in hit_cache:
                        hit_cache[key] = hit

        ranked = sorted(fused_score.items(), key=lambda x: x[1], reverse=True)
        merged: List[RetrievedChunk] = []
        for key, score in ranked[:top_k]:
            item = hit_cache[key]
            merged.append(
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
        return merged
