# Medical-Agent-Java

面向医学影像咨询与证据问答的 Java 多 Agent 平台。Java 独立实现知识入库、PDF 切块、BM25 + Milvus 混合检索、HyDE、RRF、reranker 编排、病例记忆、Skills、安全门控和运行 trace。Python 不承载 RAG 业务，只提供三个 vLLM 推理端点。

## 架构边界

| 组件 | 职责 | 默认地址 |
| --- | --- | --- |
| Medical-Agent-Java | API、RAG、Harness、多 Agent、MySQL/Redis、审计 | `http://127.0.0.1:9100` |
| vLLM Embedding | 文本/图文向量推理 | `http://127.0.0.1:8001/v1/embeddings` |
| vLLM Reranker | 候选证据重排 | `http://127.0.0.1:8002/v1/rerank` |
| vLLM Qwen-VL | HyDE 与多模态回答生成 | `http://127.0.0.1:8003/v1/chat/completions` |
| Milvus | 稠密向量索引 | `http://127.0.0.1:19530` |

主链路：`HTTP -> MedicalAgentHarness -> CoordinatorAgent -> Memory/Router/Evidence/Safety/Response Agents -> report + trace`。详细设计见 [docs/TECH_DESIGN.md](docs/TECH_DESIGN.md)。

## 构建与测试

要求 Java 17 和 Maven 3.9+。

```powershell
mvn test
mvn package
powershell -ExecutionPolicy Bypass -File scripts/smoke-local.ps1
```

当前 12 个自动化测试覆盖 Spring 上下文、API 权限、Redis 回源、中文 tokenizer/BM25、切块、RRF、协作黑板、Agent 顺序、安全复核和动态 Skills。开发环境默认使用文件型 H2；未启动 Milvus 时可设置 `$env:MILVUS_ENABLED='false'` 运行 BM25 降级模式。

## 本地启动

先启动三个 vLLM 服务，再执行：

```powershell
$env:DEMO_USER_PASSWORD='change-user-password'
$env:DEMO_ADMIN_PASSWORD='change-admin-password'
mvn spring-boot:run
```

提交纯文本或图文咨询：

```powershell
curl.exe -u medical-user:change-user-password `
  -F "question=这张影像可能提示什么？请列出证据和局限" `
  -F "image=@C:\data\example.png;type=image/png" `
  http://127.0.0.1:9100/api/v1/consultations
```

管理员上传 Java RAG 知识：

```powershell
curl.exe -u medical-admin:change-admin-password `
  -F "file=@C:\data\guideline.pdf;type=application/pdf" `
  http://127.0.0.1:9100/api/admin/v1/knowledge
```

查询 `GET /api/admin/v1/knowledge`、`GET /api/admin/v1/reports/{reportId}`、`GET /api/admin/v1/traces/{traceId}` 或 `GET /api/admin/v1/skills` 查看入库、报告、完整运行轨迹和 Skills。

## Docker 部署

```powershell
Copy-Item .env.example .env
# 修改 .env 中全部 change-* 密码，并确认三个 vLLM 地址
docker compose config --quiet
docker compose up -d --build
```

Compose 包含 Java、MySQL、Redis、Milvus、etcd 和 MinIO；GPU/vLLM 保持外部部署。Windows Docker Desktop 默认通过 `host.docker.internal` 访问宿主机 vLLM。

## 安全说明

- 系统是科研与医生辅助复核工具，不替代诊断或急诊处置。
- 原始影像只存在于单次请求内存，不写入 MySQL、Redis、日志、报告或 trace。
- 急症红旗、低证据和依赖降级会强制人工复核。
- Basic Auth 是演示方案；生产环境应接入组织身份系统并关闭演示账号。

当前完成度与外部环境状态见 [docs/PROGRESS.md](docs/PROGRESS.md)，验收证据见 [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md)。
