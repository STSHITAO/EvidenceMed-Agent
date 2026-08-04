# EvidenceMed-Agent

EvidenceMed 是面向医学影像辅助分析与循证问答的多模态 Agent 平台。它用于科研、教学和医生复核，不替代临床诊断、处方或急诊处置。

## 架构边界

```text
浏览器 / REST 客户端
        ↓
Spring Boot Web UI + API（认证、RAG、审计与安全门控）
        ├── MySQL / Redis / Milvus
        └── 私有 vLLM 推理契约（Embedding / Reranker / VLM）
```

- `apps/medical-agent-java`：唯一的业务入口，内含 Vue 3 + TypeScript 前端，负责 RAG、Agent、病例会话、安全审查与审计。
- `apps/medical-vllm-service`：仅提供三个无状态模型推理端点；不提供 RAG、病例或 Web API。
- `models/`、`datasets/`、`training/`：分别存放模型、训练数据与训练工具，均不提交敏感或大型产物。

## 安全边界

- 原始影像只用于单次请求，不写入数据库、Redis、日志或 trace。
- Redis 可保存带 TTL 的病例上下文；生产部署必须设置访问控制、加密与留存策略。
- 高风险或紧急风险结果只返回受控人工复核状态，不展示未核准模型草稿。
- 模型端点默认绑定本机并强制 API key；跨主机部署使用私有 TLS 网关。
- Docker Compose 不暴露 Milvus 端口，MinIO 凭据必须从环境变量提供。

## 快速开始

1. 启动私有 vLLM 端点，并为三个端点设置同一个 `VLLM_API_KEY`。
2. 在 `apps/medical-agent-java` 复制 `.env.example` 为 `.env`，填写基础设施密码、MinIO 凭据和同一个 `VLLM_API_KEY`。本地演示时显式设置 `DEMO_USERS_ENABLED=true`。
3. 运行 `docker compose up -d --build`，访问 `http://localhost:9100`，使用配置的 Basic Auth 账号登录。

项目布局见 [docs/PROJECT_LAYOUT.md](docs/PROJECT_LAYOUT.md)，当前进度见 [docs/PROGRESS.md](docs/PROGRESS.md)。
