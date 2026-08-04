# EvidenceMed-Agent 项目布局

```text
EvidenceMed-Agent/
├── apps/
│   ├── medical-agent-java/      # Vue+TS Web UI、API、RAG、Agent、安全与审计
│   └── medical-vllm-service/    # 三个无状态 vLLM 启动契约
├── datasets/                    # 训练数据约定（不提交原始敏感数据）
├── models/                      # 基座模型与 LoRA 挂载约定（不提交权重）
├── training/                    # 训练配置与数据处理工具
├── runtime/                     # 本地运行产物（不提交）
└── docs/                        # 架构、进度和数据说明
```

`medical-agent-java` 是唯一业务工程，前端源代码位于 `frontend/`，由 Vite 构建后随 JAR 发布；它包含身份认证、病例上下文、Java RAG、人工复核门控和审计记录。`medical-vllm-service` 不保存业务数据，也不包含 HTTP 业务 API、RAG 或向量数据库代码，只保留模型启动脚本与 vLLM 依赖。
