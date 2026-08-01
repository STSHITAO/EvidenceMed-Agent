from __future__ import annotations

from typing import List

from medical_rag.config import Settings
from medical_rag.retrieval import (
    MilvusVectorStore,
    MultiRouteRetriever,
    SparseRetriever,
    create_embedder,
    create_reranker,
)
from medical_rag.schemas import RagAnswer, RetrievedChunk
from medical_rag.vlm import QwenVLMReasoner


class MedicalRAGPipeline:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings

        self.embedder = create_embedder(settings.embedding)

        self.store = MilvusVectorStore(settings.milvus)
        self.store.connect()
        self.store.load()

        self.sparse_retriever: SparseRetriever | None = None
        if settings.sparse.enabled and settings.sparse.corpus_path:
            sparse = SparseRetriever(settings.sparse)
            sparse.load()
            self.sparse_retriever = sparse

        self.retriever = MultiRouteRetriever(
            embedder=self.embedder,
            store=self.store,
            embedding_config=settings.embedding,
            sparse_retriever=self.sparse_retriever,
            route_top_k=max(4, settings.project.top_k_recall // 2),
        )

        self.reranker = create_reranker(settings.reranker)
        self.reasoner = QwenVLMReasoner(settings.vlm)

    @staticmethod
    def _build_evidence_blocks(items: List[RetrievedChunk]) -> List[str]:
        blocks: List[str] = []
        for item in items:
            if item.modality == "image":
                text = (
                    f"来源: {item.source} | type: image | score: {item.score:.4f}\n"
                    f"图像描述: {item.content}"
                )
            else:
                text = (
                    f"来源: {item.source} | chunk: {item.chunk_index} | type: {item.modality} | score: {item.score:.4f}\n"
                    f"{item.content}"
                )
            blocks.append(text)
        return blocks

    def _collect_evidence_image_paths(self, items: List[RetrievedChunk]) -> List[str]:
        image_paths: List[str] = []
        for item in items:
            if item.asset_path and item.modality in {"image", "multimodal"}:
                image_paths.append(item.asset_path)
            if len(image_paths) >= self.settings.project.top_k_evidence_images:
                break
        return image_paths

    def _build_rerank_query(self, question: str, hyde_text: str) -> str:
        if not hyde_text.strip():
            return question
        return f"{question}\n\n[HyDE]\n{hyde_text.strip()}"

    def _generate_hyde(self, image_path: str, question: str) -> str:
        if not self.settings.hyde.enabled:
            return ""
        return self.reasoner.generate_hypothetical_document(
            image_path=image_path,
            question=question,
            prompt=self.settings.hyde.prompt,
            max_new_tokens=self.settings.hyde.max_new_tokens,
            temperature=self.settings.hyde.temperature,
        )

    def ask(self, image_path: str, question: str) -> RagAnswer:
        hyde_text = self._generate_hyde(image_path=image_path, question=question)

        recalled = self.retriever.recall(
            query=question,
            image_path=image_path,
            hyde_text=hyde_text,
            top_k=self.settings.project.top_k_recall,
        )
        reranked = self.reranker.rerank(
            self._build_rerank_query(question=question, hyde_text=hyde_text),
            recalled,
            top_k=self.settings.project.top_k_rerank,
        )

        if not reranked:
            reranked = [
                RetrievedChunk(
                    doc_id="none",
                    source="none",
                    chunk_index=-1,
                    content="未检索到可用医学证据，请补充文本指南或参考影像并重建索引。",
                    score=0.0,
                    modality="text",
                    asset_path="",
                )
            ]

        evidence_blocks = self._build_evidence_blocks(reranked)
        evidence_image_paths = self._collect_evidence_image_paths(reranked)
        answer = self.reasoner.generate(
            image_path=image_path,
            question=question,
            evidence_blocks=evidence_blocks,
            evidence_image_paths=evidence_image_paths,
        )
        return RagAnswer(answer=answer, evidence=reranked, hyde_text=hyde_text)
