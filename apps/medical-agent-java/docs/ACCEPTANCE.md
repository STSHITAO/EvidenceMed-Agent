# Medical-Agent-Java 验收清单

## 自动化验证

- [x] `mvn clean verify` 通过。
- [x] Vue 3 + TypeScript 前端经 Vite 构建，并包含在最终 JAR 的 `static/` 资源中。
- [x] 未认证的医疗 API 返回 401，管理员 API 与普通 API 权限隔离。
- [x] Redis 不可用时可从 MySQL 恢复病例上下文。
- [x] BM25、切块、RRF、Agent 顺序和 Skill 加载有单元测试。
- [x] 紧急红旗与无证据高风险结果进入人工复核，不返回模型草稿。
- [x] 提示词中的检索证据使用非可信边界包装。

## 部署验收

- [x] Compose 不向宿主机暴露 Milvus。
- [x] MinIO 与 vLLM 密钥由环境变量提供，演示账号默认关闭。
- [ ] 使用真实 GPU 的 vLLM API key 与私有 TLS 网关进行端到端验证。
- [ ] 经临床团队审批红旗覆盖范围、人工复核 SLA 与知识库准入流程。
