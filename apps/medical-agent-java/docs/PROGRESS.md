# Medical-Agent-Java 当前进度

最后更新：2026-07-29

## 重构结论

- Java 主工程已归档到 `apps/medical-agent-java`；原 Python 项目位于 `apps/medical-vllm-service`，仅保留原 vLLM 模型启动方案和迁移对照，不进入 Java 主链路。
- Java 拥有 RAG、Harness、多 Agent、数据、安全和 API；Python 只提供 vLLM 推理。
- 已清理重复的实体、BM25/RRF、VectorStore、模型网关和 Agent Runtime，仅保留一套连续实现。

## 已完成

- 阶段 1：产品/技术/验收文档、Maven、三个 vLLM 端点、PDFBox、Milvus SDK 与配置。
- 阶段 2：用户、病例、消息、知识、报告、trace 实体；Basic Auth；MySQL；Redis TTL 记忆与回源。
- 阶段 3：PDF/Markdown/TXT 入库、哈希幂等、章节切块、中英文 tokenizer、BM25、Milvus、HyDE、RRF、reranker 和降级链路。
- 阶段 4：Harness、Coordinator、协作黑板、五个 Agent、Agent step/event trace、三个动态医疗 `SKILL.md`。
- 阶段 5：图文咨询页面、管理员知识/报告/trace/Skills API、统一安全错误响应；原始影像不持久化。
- 阶段 6：Dockerfile、MySQL/Redis/Milvus Compose、环境模板、README；Compose 配置校验和 JAR 构建通过。
- `mvn clean verify`：12 个测试通过，0 失败；`medical-agent-java-0.1.0.jar` 已重新生成。
- 原 vLLM 启动脚本已按历史双卡运行参数恢复，并通过 `bash -n` 静态语法检查；未下载或加载模型。

## 部署状态

- Maven/JAR：已验证。
- Spring H2 测试环境：完整上下文和随机端口 API 已验证。
- 本地打包烟测：JAR 健康接口返回 `status=UP, ragOwner=java`；在 vLLM/Milvus 离线条件下提交“呼吸困难”问题，返回 `EMERGENCY + humanReviewRequired=true` 并生成 trace。
- Docker Compose：`docker compose -f docker-compose.yml --env-file .env.example config --quiet` 已验证。
- MySQL/Redis/Milvus 容器：本轮未实际拉起，避免使用示例密码创建持久数据。
- 三个 vLLM 服务：保留原双卡部署参数与 8001/8002/8003 接口；按当前要求不在本机加载模型，真实模型端到端验证待双卡 GPU 环境执行。

## 剩余外部验收

- 用真实脱敏医学知识和影像执行一次入库、混合检索、问答、报告与 trace 冒烟测试。
- 记录真实 vLLM embedding 维度；若不是 2048，修改 `EMBEDDING_DIMENSION` 后重建 Milvus collection。
- 生产上线前替换 Basic Auth、轮换全部密钥并完成医疗/隐私合规评审。
