#!/usr/bin/env bash
set -euo pipefail

# Keep the original three-service vLLM contract used by Medical-Agent-Java.
# Usage: bash scripts/serve_vllm.sh embed|rerank|vlm

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${ROOT_DIR}/.env"
  set +a
fi

MODE="${1:-}"
VLLM_HOST="${VLLM_HOST:-127.0.0.1}"
VLLM_API_KEY="${VLLM_API_KEY:?VLLM_API_KEY must be set before starting a model service}"

case "${MODE}" in
  embed)
    MODEL="${EMBED_MODEL:-/root/autodl-tmp/Qwen/Qwen/Qwen3-VL-Embedding-2B}"
    exec vllm serve "${MODEL}" \
      --host "${VLLM_HOST}" \
      --port "${EMBED_PORT:-8001}" \
      --api-key "${VLLM_API_KEY}" \
      --task embed \
      --trust-remote-code \
      --dtype "${EMBED_DTYPE:-bfloat16}" \
      --max-model-len "${EMBED_MAX_MODEL_LEN:-1024}" \
      --enforce-eager \
      --served-model-name "${EMBED_SERVED_MODEL_NAME:-Qwen/Qwen3-VL-Embedding-2B}" \
      --gpu-memory-utilization "${EMBED_GPU_MEMORY_UTILIZATION:-0.45}"
    ;;
  rerank)
    MODEL="${RERANK_MODEL:-/root/autodl-tmp/Qwen/Qwen/Qwen3-VL-Reranker-2B}"
    exec vllm serve "${MODEL}" \
      --host "${VLLM_HOST}" \
      --port "${RERANK_PORT:-8002}" \
      --api-key "${VLLM_API_KEY}" \
      --task score \
      --trust-remote-code \
      --dtype "${RERANK_DTYPE:-bfloat16}" \
      --max-model-len "${RERANK_MAX_MODEL_LEN:-1024}" \
      --enforce-eager \
      --served-model-name "${RERANK_SERVED_MODEL_NAME:-Qwen/Qwen3-VL-Reranker-2B}" \
      --gpu-memory-utilization "${RERANK_GPU_MEMORY_UTILIZATION:-0.45}"
    ;;
  vlm)
    MODEL="${VLM_MODEL:-${VLM_MERGED_MODEL:-/root/autodl-tmp/Qwen/Qwen3-VL-8B-Instruct-merged-lora-20260308}}"
    LIMIT_MM_PER_PROMPT="${VLM_LIMIT_MM_PER_PROMPT:-}"
    MM_PROCESSOR_KWARGS="${VLM_MM_PROCESSOR_KWARGS:-}"
    [[ -n "${LIMIT_MM_PER_PROMPT}" ]] || LIMIT_MM_PER_PROMPT='{"image":1}'
    [[ -n "${MM_PROCESSOR_KWARGS}" ]] || MM_PROCESSOR_KWARGS='{"max_pixels":262144}'
    exec vllm serve "${MODEL}" \
      --host "${VLLM_HOST}" \
      --port "${VLM_PORT:-8003}" \
      --api-key "${VLLM_API_KEY}" \
      --trust-remote-code \
      --dtype "${VLM_DTYPE:-bfloat16}" \
      --max-model-len "${VLM_MAX_MODEL_LEN:-2048}" \
      --enforce-eager \
      --served-model-name "${VLM_SERVED_MODEL_NAME:-qwen3-vl-medical}" \
      --gpu-memory-utilization "${VLM_GPU_MEMORY_UTILIZATION:-0.63}" \
      --limit-mm-per-prompt "${LIMIT_MM_PER_PROMPT}" \
      --mm-processor-kwargs "${MM_PROCESSOR_KWARGS}" \
      --skip-mm-profiling
    ;;
  *)
    echo "Usage: $0 embed|rerank|vlm" >&2
    exit 2
    ;;
esac
