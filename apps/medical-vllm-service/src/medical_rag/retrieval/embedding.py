from __future__ import annotations

import base64
import mimetypes
import os
from pathlib import Path
from typing import Iterable, List, Protocol

import numpy as np
import requests

from medical_rag.config import EmbeddingConfig
from medical_rag.schemas import EmbedInput


class Embedder(Protocol):
    def encode(self, items: Iterable[str | EmbedInput]) -> np.ndarray:
        ...


class APIEmbedder:
    """HTTP API embedder for OpenAI-compatible multimodal embedding endpoints."""

    def __init__(
        self,
        model_name: str,
        api_base_url: str,
        api_key: str,
        api_key_env: str,
        api_embedding_path: str,
        api_style: str,
        timeout_sec: int,
        batch_size: int,
        default_instruction: str,
    ) -> None:
        self.model_name = model_name
        self.api_base_url = api_base_url.rstrip("/")
        self.api_key = api_key or os.getenv(api_key_env, "")
        self.api_embedding_path = api_embedding_path
        self.api_style = api_style
        self.timeout_sec = timeout_sec
        self.batch_size = batch_size
        self.default_instruction = default_instruction

        if not self.api_base_url:
            raise ValueError("embedding.api_base_url is required when embedding.provider=api")

    def _headers(self) -> dict:
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        return headers

    def _parse_embeddings(self, data: dict) -> List[List[float]]:
        if "data" in data and isinstance(data["data"], list):
            ordered = sorted(data["data"], key=lambda x: x.get("index", 0))
            return [item["embedding"] for item in ordered]
        if "embeddings" in data and isinstance(data["embeddings"], list):
            return data["embeddings"]
        if "output" in data and isinstance(data["output"], dict) and "embeddings" in data["output"]:
            return data["output"]["embeddings"]
        raise ValueError(f"Unsupported embedding API response format: keys={list(data.keys())}")

    @staticmethod
    def _normalize_inputs(items: Iterable[str | EmbedInput]) -> List[EmbedInput]:
        normalized: List[EmbedInput] = []
        for item in items:
            if isinstance(item, EmbedInput):
                normalized.append(item)
            else:
                normalized.append(EmbedInput(text=str(item)))
        return normalized

    @staticmethod
    def _image_to_data_url(image_path: str) -> str:
        mime, _ = mimetypes.guess_type(image_path)
        if not mime:
            mime = "image/png"
        with open(image_path, "rb") as f:
            encoded = base64.b64encode(f.read()).decode("utf-8")
        return f"data:{mime};base64,{encoded}"

    def _embed_text_batch(self, batch: List[EmbedInput]) -> List[List[float]]:
        url = f"{self.api_base_url}{self.api_embedding_path}"
        texts = [item.text for item in batch]
        if self.api_style == "openai":
            payload = {"model": self.model_name, "input": texts}
        else:
            payload = {"model": self.model_name, "texts": texts}
        resp = requests.post(url, headers=self._headers(), json=payload, timeout=self.timeout_sec)
        resp.raise_for_status()
        body = resp.json()
        return self._parse_embeddings(body)

    def _embed_multimodal_item(self, item: EmbedInput) -> List[float]:
        url = f"{self.api_base_url}{self.api_embedding_path}"
        instruction = item.instruction or self.default_instruction

        user_content: List[dict] = []
        if item.image_path:
            image_path = Path(item.image_path)
            if not image_path.exists():
                raise FileNotFoundError(f"Embedding image not found: {item.image_path}")
            user_content.append(
                {
                    "type": "image_url",
                    "image_url": {"url": self._image_to_data_url(str(image_path))},
                }
            )
        if item.text:
            user_content.append({"type": "text", "text": item.text})
        if not user_content:
            user_content.append({"type": "text", "text": ""})

        payload = {
            "model": self.model_name,
            "messages": [
                {"role": "system", "content": [{"type": "text", "text": instruction}]},
                {"role": "user", "content": user_content},
                {"role": "assistant", "content": [{"type": "text", "text": ""}]},
            ],
            "encoding_format": "float",
            "continue_final_message": True,
            "add_special_tokens": True,
        }

        resp = requests.post(url, headers=self._headers(), json=payload, timeout=self.timeout_sec)
        resp.raise_for_status()
        body = resp.json()
        vectors = self._parse_embeddings(body)
        if not vectors:
            raise ValueError("Embedding endpoint returned no vectors for multimodal item.")
        return vectors[0]

    def encode(self, items: Iterable[str | EmbedInput]) -> np.ndarray:
        normalized = self._normalize_inputs(items)
        if not normalized:
            return np.zeros((0, 1), dtype=np.float32)

        outputs: List[List[float] | None] = [None] * len(normalized)
        text_buffer: List[EmbedInput] = []
        text_indices: List[int] = []

        def flush_text_buffer() -> None:
            if not text_buffer:
                return
            vectors = self._embed_text_batch(text_buffer)
            for idx, vector in zip(text_indices, vectors):
                outputs[idx] = vector
            text_buffer.clear()
            text_indices.clear()

        for idx, item in enumerate(normalized):
            if item.image_path or item.instruction:
                flush_text_buffer()
                outputs[idx] = self._embed_multimodal_item(item)
                continue

            text_buffer.append(item)
            text_indices.append(idx)
            if len(text_buffer) >= self.batch_size:
                flush_text_buffer()

        flush_text_buffer()

        final_vectors: List[List[float]] = []
        for vector in outputs:
            if vector is None:
                raise ValueError("Embedding result missing for one or more items.")
            final_vectors.append(vector)
        return np.asarray(final_vectors, dtype=np.float32)


def create_embedder(config: EmbeddingConfig) -> Embedder:
    provider = (config.provider or "api").lower()
    if provider != "api":
        raise ValueError(
            f"Unsupported embedding.provider={config.provider}. "
            "This project only supports embedding.provider=api (vLLM /v1/embeddings)."
        )

    return APIEmbedder(
        model_name=config.model_name,
        api_base_url=config.api_base_url,
        api_key=config.api_key,
        api_key_env=config.api_key_env,
        api_embedding_path=config.api_embedding_path,
        api_style=config.api_style,
        timeout_sec=config.timeout_sec,
        batch_size=config.batch_size,
        default_instruction=config.default_instruction,
    )
