# Medical-Agent-Java 开发规则

- Java 17、Spring Boot；Python 仅启动 embedding、reranker、Qwen-VL 三个 vLLM 推理端点。
- PDF 解析、切块、BM25、Milvus、HyDE、RRF、证据编排、Harness 和 Agent 调度必须在 Java 内完成。
- Controller 不直接调用模型；所有问答必须经过 `MedicalAgentHarness`、急症硬规则与 `SafetyCriticAgent`/受控安全兜底。
- MySQL 保存业务事实、知识元数据和 trace；Redis 只保存有 TTL 的短期上下文，不保存原始影像。
- 原始影像不得写入日志、数据库、Skill 或 trace；测试数据必须为合成或脱敏内容。
- 包按 `api/application/domain/infrastructure` 边界组织，禁止跨层循环依赖和无用途抽象。
- 每阶段完成后运行测试，并同步更新 `docs/PROGRESS.md` 与 `docs/ACCEPTANCE.md`。
