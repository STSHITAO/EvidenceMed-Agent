# Medical-Agent-Java 验收清单

## 工程与数据

- [x] Java 与 Python 服务边界写入 README 和技术设计。
- [x] Milvus Java SDK、PDFBox 和三个 vLLM 端点配置完成。
- [x] `mvn test`：12 个测试全部通过；可运行 JAR 构建成功。
- [x] MySQL 模型覆盖用户、病例、消息、知识、报告和 trace。
- [x] Redis 不可用时从 MySQL 恢复上下文，且不阻断健康检查。

## Java RAG

- [x] Java 解析 PDF、Markdown、TXT，稳定切块并按 SHA-256 幂等入库。
- [x] Java BM25 支持中英文医学文本且有单元测试。
- [x] Java 调用 vLLM embedding 并读写 Milvus；Milvus 故障时降级 BM25。
- [x] Java 实现 HyDE、RRF 和 reranker，返回带来源证据。
- [x] Python 业务 `/ask` 不在 Java 主链路中使用。

## Agent 与安全

- [x] 普通用户可提交 JPEG/PNG、问题和可选会话 ID。
- [x] Harness 保存报告、Agent step 和协作黑板事件。
- [x] 证据不足、依赖降级和急症红旗触发人工复核。
- [x] 原始影像不写入数据库、Redis、日志或 trace。
- [x] 管理员可上传/查询知识并查询报告、trace 和 Skills。
- [x] vLLM 端点不可用时返回降级结果或脱敏错误，不泄露内部堆栈。
- [x] SafetyReviewAgent 在 ResponseAgent 之后最终执行；黑板拒绝原始影像字节，均有单元测试。

## 部署

- [x] Docker Compose 定义 Java、MySQL、Redis、Milvus、etcd、MinIO；vLLM 地址外部配置。
- [x] Compose 配置解析通过，JAR 构建通过。
- [x] 原 vLLM 双卡部署脚本已恢复并通过 Shell 静态语法检查；模型、端口和 Java HTTP 调用契约一致。
- [x] 本地 H2/BM25 降级模式启动、健康接口和急症安全降级烟测通过。
- [ ] 在实际 GPU/vLLM 环境完成入库、检索、图文问答和审计端到端冒烟测试。
