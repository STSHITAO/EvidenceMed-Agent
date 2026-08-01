#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from medical_rag.config import load_settings  # noqa: E402
from medical_rag.retrieval import MilvusVectorStore, create_embedder, write_sparse_corpus  # noqa: E402
from medical_rag.schemas import EmbedInput  # noqa: E402
from medical_rag.utils.text_chunker import build_knowledge_items  # noqa: E402


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build Medical-RAG Milvus index from repository root.")
    parser.add_argument("--config", type=str, default=str(ROOT / "config" / "settings.lite.yaml"))
    parser.add_argument("--drop-old", action="store_true", help="Drop existing collection before indexing.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    settings = load_settings(args.config)

    items = build_knowledge_items(
        doc_dir=settings.knowledge.doc_dir,
        image_dir=settings.knowledge.image_dir,
        chunk_size=settings.knowledge.chunk_size,
        chunk_overlap=settings.knowledge.chunk_overlap,
    )
    if not items:
        raise RuntimeError(
            f"No knowledge items generated from doc_dir={settings.knowledge.doc_dir} image_dir={settings.knowledge.image_dir}"
        )

    embed_inputs = []
    for item in items:
        if item.modality == "image":
            embed_inputs.append(
                EmbedInput(
                    text=item.content,
                    image_path=item.asset_path,
                    instruction=settings.embedding.image_document_instruction,
                )
            )
        else:
            embed_inputs.append(
                EmbedInput(
                    text=item.content,
                )
            )

    embedder = create_embedder(settings.embedding)
    vectors = embedder.encode(embed_inputs)
    dim = vectors.shape[1]

    store = MilvusVectorStore(settings.milvus)
    store.connect()
    if args.drop_old:
        store.recreate_collection(vector_dim=dim)
    else:
        store.ensure_collection(vector_dim=dim)

    inserted = store.insert_chunks(items, vectors)
    sparse_written = 0
    if settings.sparse.enabled and settings.sparse.corpus_path:
        sparse_written = write_sparse_corpus(settings.sparse.corpus_path, items)
    text_count = sum(1 for item in items if item.modality == "text")
    image_count = sum(1 for item in items if item.modality == "image")
    print(
        f"[Medical-RAG] items={len(items)} text={text_count} image={image_count} "
        f"inserted={inserted} sparse_items={sparse_written} collection={settings.milvus.collection_name}"
    )


if __name__ == "__main__":
    main()
