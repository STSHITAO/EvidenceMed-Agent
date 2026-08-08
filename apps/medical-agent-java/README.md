# medical-agent-java

EvidenceMed 的业务主工程：提供经过 Basic Auth 保护的 Vue 3 + TypeScript 浏览器工作台与 REST API，并在 Java 内完成病例会话、RAG、Agent 编排、安全审查和审计。

Agent runtime 不使用固定执行顺序。Coordinator 将任务写入共享黑板，根据依赖和能力动态调度：`CaseMemoryService`、`AgentRuntimePreprocessor` 与 `JavaMedicalRagService` 执行确定性工作；`EvidencePlanningAgent`、`MedicalReasoningAgent` 和 `SafetyCriticAgent` 只处理需要自主规划、生成或批判审查的任务。同一轮无依赖任务通过受控线程池并行执行，结果由 Coordinator 串行合并，避免共享上下文竞态。

## 本地运行

```powershell
$env:MILVUS_ENABLED='false'
$env:DEMO_USERS_ENABLED='true'
$env:DEMO_USER_PASSWORD='change-user-password'
$env:DEMO_ADMIN_PASSWORD='change-admin-password'
$env:EMBEDDING_API_KEY='change-this-vllm-api-key'
$env:RERANKER_API_KEY='change-this-vllm-api-key'
$env:VLM_API_KEY='change-this-vllm-api-key'
mvn spring-boot:run
```

访问 `http://localhost:9100`。前端位于 `frontend/`，由 Vite 构建并在 Maven 的 `generate-resources` 阶段打包进 JAR；它与 API 共用 Spring Security 的 Basic Auth。

## 关键安全行为

- 仅接受最大 20 MB、且内容签名匹配的 JPEG/PNG 影像。
- 原始影像不进入持久化、Redis、日志或 trace。
- 病例上下文在 Redis 中以 TTL 缓存；由部署环境负责网络隔离和加密。
- 证据、病历和用户输入被标记为非可信提示词内容。
- `HIGH` 与 `EMERGENCY` 结果会返回人工复核状态而不是模型草稿。
- 急症红旗在模型调用前短路；安全审查不通过会动态创建修订任务，最多修订两次后转人工复核。

运行验证：

```powershell
mvn clean verify
docker compose --env-file .env.example config --quiet
```
