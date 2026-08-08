# Medical-Agent-Java 验收清单

## 自动化验证

- [x] `mvn clean verify` 通过。
- [x] Vue 3 + TypeScript 前端经 Vite 构建，并包含在最终 JAR 的 `static/` 资源中。
- [x] 未认证的医疗 API 返回 401，管理员 API 与普通 API 权限隔离。
- [x] Redis 不可用时可从 MySQL 恢复病例上下文。
- [x] BM25、切块、RRF、动态任务依赖、能力调度和 Skill 加载有单元测试。
- [x] 紧急红旗与无证据高风险结果进入人工复核，不返回模型草稿。
- [x] 提示词中的检索证据使用非可信边界包装。
- [x] 源码不存在按 Agent 名称硬编码的固定顺序；记忆与证据检索并发测试通过。
- [x] 简单问候跳过 RAG，证据充分时不启动 EvidencePlanningAgent。
- [x] 初次无证据时动态生成一次补充检索任务。
- [x] 安全拒绝会动态派生修订任务，连续两次不通过则返回受控人工复核说明。
- [x] 急症红旗在记忆、RAG 和生成模型调用前短路。

## 部署验收

- [x] Compose 不向宿主机暴露 Milvus。
- [x] MinIO 与 vLLM 密钥由环境变量提供，演示账号默认关闭。
- [ ] 使用真实 GPU 的 vLLM API key 与私有 TLS 网关进行端到端验证。
- [ ] 经临床团队审批红旗覆盖范围、人工复核 SLA 与知识库准入流程。
