# medical-vllm-service

本目录只维护 EvidenceMed Java 主工程所需的三个无状态 vLLM 推理契约：

| 服务 | 默认端口 | 接口 |
| --- | --- | --- |
| Embedding | 8001 | `/v1/embeddings` |
| Reranker | 8002 | `/v1/rerank` |
| Vision-Language Model | 8003 | `/v1/chat/completions` |

这里不包含 RAG、Milvus、BM25、病例存储、Web 页面或业务 API；它们全部由 `apps/medical-agent-java` 承担。

## 启动

安装与 GPU 环境匹配的 vLLM 后，复制 `.env.example` 为 `.env`，填写模型路径和一个强随机 `VLLM_API_KEY`。默认仅绑定 `127.0.0.1`：

```bash
bash scripts/serve_vllm.sh embed
bash scripts/serve_vllm.sh rerank
bash scripts/serve_vllm.sh vlm
```

双 GPU 主机可使用：

```bash
bash scripts/run_dual_gpu_stack.sh start
```

`VLLM_API_KEY` 是必填项，三个 Java 客户端使用同一密钥。若 Java 服务与 GPU 主机分离，请通过私有网络中的 TLS 反向代理暴露模型端点；不要将 vLLM 端口直接开放至公网。
