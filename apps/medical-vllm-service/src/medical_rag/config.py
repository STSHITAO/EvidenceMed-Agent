from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict

import yaml


@dataclass
class ProjectConfig:
    name: str
    language: str
    top_k_recall: int
    top_k_rerank: int
    top_k_evidence_images: int = 2


@dataclass
class KnowledgeConfig:
    doc_dir: str
    chunk_size: int
    chunk_overlap: int
    image_dir: str = ""


@dataclass
class MilvusConfig:
    uri: str = ""
    host: str = "127.0.0.1"
    port: int = 19530
    user: str = ""
    password: str = ""
    db_name: str = "default"
    collection_name: str = "medical_guidelines"
    consistency_level: str = "Strong"


@dataclass
class SparseConfig:
    enabled: bool = True
    top_k: int = 8
    corpus_path: str = ""
    bm25_k1: float = 1.5
    bm25_b: float = 0.75


@dataclass
class EmbeddingConfig:
    model_name: str
    provider: str = "api"
    use_fp16: bool = True
    batch_size: int = 16
    max_length: int = 1024
    api_base_url: str = ""
    api_key: str = ""
    api_key_env: str = "MODELSCOPE_API_TOKEN"
    api_embedding_path: str = "/v1/embeddings"
    api_style: str = "openai"
    timeout_sec: int = 60
    default_instruction: str = "Represent the medical content for retrieval."
    query_instruction: str = "Represent the medical retrieval query."
    multimodal_query_instruction: str = "Represent the medical image and question for retrieval."
    document_instruction: str = "Represent the medical guideline chunk for retrieval."
    image_document_instruction: str = "Represent the medical reference image for retrieval."
    hyde_instruction: str = "Represent the hypothetical medical evidence for retrieval."


@dataclass
class RerankerConfig:
    model_name: str
    provider: str = "api"
    use_fp16: bool = True
    batch_size: int = 16
    api_base_url: str = ""
    api_key: str = ""
    api_key_env: str = "MODELSCOPE_API_TOKEN"
    api_rerank_path: str = "/v1/rerank"
    api_style: str = "openai"
    timeout_sec: int = 60


@dataclass
class VLMConfig:
    base_model_path: str
    lora_adapter_path: str
    device: str
    dtype: str
    max_new_tokens: int
    temperature: float
    backend: str = "vllm_openai"
    model_name: str = ""
    api_base_url: str = ""
    api_key: str = ""
    api_key_env: str = "VLLM_API_KEY"
    api_chat_path: str = "/v1/chat/completions"
    request_timeout_sec: int = 120


@dataclass
class HyDEConfig:
    enabled: bool = False
    max_new_tokens: int = 160
    temperature: float = 0.2
    prompt: str = (
        "You are generating a concise hypothetical medical evidence note for retrieval. "
        "Based on the user's question and image if provided, write 4-6 sentences describing "
        "possible radiology findings, likely diagnoses, and guideline-style keywords. "
        "Do not mention uncertainty policy. Do not answer in bullet points."
    )


@dataclass
class ServiceConfig:
    host: str
    port: int
    share: bool


@dataclass
class Settings:
    project: ProjectConfig
    knowledge: KnowledgeConfig
    milvus: MilvusConfig
    sparse: SparseConfig
    embedding: EmbeddingConfig
    reranker: RerankerConfig
    vlm: VLMConfig
    hyde: HyDEConfig
    service: ServiceConfig


def _read_yaml(path: str | Path) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    return data


def load_settings(path: str | Path) -> Settings:
    data = _read_yaml(path)
    return Settings(
        project=ProjectConfig(**data["project"]),
        knowledge=KnowledgeConfig(**data["knowledge"]),
        milvus=MilvusConfig(**data["milvus"]),
        sparse=SparseConfig(**data.get("sparse", {})),
        embedding=EmbeddingConfig(**data["embedding"]),
        reranker=RerankerConfig(**data["reranker"]),
        vlm=VLMConfig(**data["vlm"]),
        hyde=HyDEConfig(**data.get("hyde", {})),
        service=ServiceConfig(**data["service"]),
    )
