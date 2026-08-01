#!/usr/bin/env bash
set -euo pipefail

# Original dual-GPU layout:
#   GPU 0: embedding (8001) + reranker (8002)
#   GPU 1: multimodal VLM (8003)

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVE_SCRIPT="${ROOT_DIR}/scripts/serve_vllm.sh"
STATE_DIR="${ROOT_DIR}/.runtime/vllm"
LOG_DIR="${ROOT_DIR}/logs/dual_gpu"

mkdir -p "${STATE_DIR}" "${LOG_DIR}"

pid_file() {
  printf '%s/%s.pid' "${STATE_DIR}" "$1"
}

is_running() {
  local service="$1"
  local file
  file="$(pid_file "${service}")"
  [[ -f "${file}" ]] && kill -0 "$(<"${file}")" 2>/dev/null
}

start_service() {
  local service="$1"
  local gpu="$2"
  if is_running "${service}"; then
    echo "${service} already running (pid $(<"$(pid_file "${service}")"))"
    return
  fi
  nohup env CUDA_VISIBLE_DEVICES="${gpu}" bash "${SERVE_SCRIPT}" "${service}" \
    >"${LOG_DIR}/${service}.log" 2>&1 &
  echo "$!" >"$(pid_file "${service}")"
  echo "started ${service} on GPU ${gpu} (pid $!)"
}

stop_service() {
  local service="$1"
  local file
  file="$(pid_file "${service}")"
  if ! is_running "${service}"; then
    rm -f "${file}"
    echo "${service} is not running"
    return
  fi
  local pid
  pid="$(<"${file}")"
  kill "${pid}"
  for _ in {1..30}; do
    kill -0 "${pid}" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "${pid}" 2>/dev/null; then
    kill -TERM "${pid}"
  fi
  rm -f "${file}"
  echo "stopped ${service}"
}

status_service() {
  local service="$1"
  if is_running "${service}"; then
    echo "${service}: RUNNING (pid $(<"$(pid_file "${service}")"))"
  else
    echo "${service}: STOPPED"
  fi
}

case "${1:-status}" in
  start)
    start_service embed 0
    start_service rerank 0
    start_service vlm 1
    ;;
  stop)
    stop_service vlm
    stop_service rerank
    stop_service embed
    ;;
  restart)
    "$0" stop
    "$0" start
    ;;
  status)
    status_service embed
    status_service rerank
    status_service vlm
    ;;
  *)
    echo "Usage: $0 start|stop|restart|status" >&2
    exit 2
    ;;
esac
