# EvidenceMed-Agent 开发规则

- `apps/medical-agent-java` 是业务主工程，负责 RAG、Agent、数据与安全。
- `apps/medical-vllm-service` 只维护 vLLM 模型服务契约，不承载业务 RAG。
- 模型权重只放 `models/`，训练数据只放 `datasets/`，训练工具只放 `training/`。
- 不提交基座模型、训练缓存、数据库、日志、构建产物或患者敏感数据。
- 修改目录或模型接口后，同步更新根 README、`docs/PROJECT_LAYOUT.md` 和 `docs/PROGRESS.md`。
- 医疗输出必须经过安全审查；原始影像和敏感字段不得进入日志、Redis 或 trace。
