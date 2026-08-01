# EvidenceMed-Agent

面向医学影像辅助分析与专业知识问答的多模态医疗 Agent 平台。系统以 Java 为业务主链路，将病例上下文、多 Agent 协作、医学知识检索、安全复核和审计追踪统一编排，并通过 vLLM 接入图文向量、证据重排与视觉语言模型。

> 本项目用于科研、教学和医生辅助复核，不替代临床诊断、处方或急诊处置。

## 核心能力

- **多模态医疗咨询**：接收医学影像与自然语言问题，结合病例上下文和外部医学证据生成结构化答复。
- **多 Agent 协作**：由协调 Agent 依次调度病例记忆、临床路由、证据检索、回答生成和安全审查，并通过协作黑板共享任务状态与产物。
- **Java Medical-RAG**：支持 PDF、Markdown 和纯文本知识入库，采用 BM25、Milvus HNSW、HyDE、RRF 和 reranker 完成混合检索与证据精排。
- **分层病例记忆**：Redis 保存短期会话状态，MySQL 持久化病例、报告、Agent 步骤和协作事件，支持上下文裁剪与历史回源。
- **动态医疗 Skills**：从 `SKILL.md` 加载证据质量、用药安全和紧急就医等领域规则，高风险回答必须通过安全复核。
- **可观测与可审计**：每次运行生成 report、trace、Agent step 和 collaboration event，便于复盘模型调用、检索证据与安全决策。

## 系统架构

```text
Web / REST Client
        │
        ▼
Spring Boot WebFlux API
        │
        ▼
Medical Agent Orchestrator
        ├── Case Memory Agent ───────── Redis / MySQL
        ├── Clinical Router Agent
        ├── Evidence Retriever Agent ── BM25 + Milvus + HyDE + RRF
        ├── Medical Response Agent ──── vLLM Qwen3-VL
        └── Safety Review Agent ─────── Medical Skills
                    │
                    ▼
          Report + Trace + Evidence

Model endpoints: Embedding :8001 · Reranker :8002 · VLM :8003
```

Java 应用负责业务编排、RAG、数据持久化和安全治理；Python 目录负责维持原有 vLLM 模型服务与 OpenAI 兼容接口。两者通过 HTTP 解耦，可分别部署在应用服务器与 GPU 服务器。

## 技术栈

| 领域 | 技术 |
| --- | --- |
| 业务服务 | Java 17、Spring Boot 3、WebFlux、Spring Security、JPA |
| Agent 与检索 | Multi-Agent、Skills、BM25、HyDE、RRF、Milvus HNSW |
| 数据与缓存 | MySQL、Redis、Milvus、MinIO、etcd |
| 模型与训练 | Qwen3-VL、LoRA、vLLM、LLaMA-Factory |
| 工程化 | Maven、Docker Compose、JUnit 5 |

## 项目结构

```text
EvidenceMed-Agent/
├── apps/
│   ├── medical-agent-java/       # Java 业务、RAG、Agent、数据与安全
│   └── medical-vllm-service/     # Embedding、Reranker、VLM 模型服务
├── datasets/                     # 训练数据目录规范与数据说明
├── models/                       # 基座模型和 LoRA Adapter 挂载规范
├── training/                     # 训练配置、数据处理 Notebook 与 LLaMA-Factory
├── docs/                         # 架构、数据模型、验收与项目文档
└── runtime/                      # 本地运行产物目录
```

模型权重、训练数据、数据库文件和运行日志按安全规范保存在本地或对象存储中，不提交到 Git。目录约定见 [数据与模型说明](docs/DATA_AND_MODELS.md) 和 [项目结构说明](docs/PROJECT_LAYOUT.md)。

## 快速开始

### 1. 环境要求

- Java 17
- Maven 3.9+
- Docker Desktop 或 Docker Engine + Compose
- Linux NVIDIA GPU 环境与 vLLM（完整模型推理）

### 2. 启动模型服务

在 GPU 服务器准备 Qwen3-VL Embedding、Reranker、VLM 基座模型及 LoRA Adapter，然后启动三个服务：

```bash
cd apps/medical-vllm-service

CUDA_VISIBLE_DEVICES=0 bash scripts/serve_vllm.sh embed
CUDA_VISIBLE_DEVICES=0 bash scripts/serve_vllm.sh rerank
CUDA_VISIBLE_DEVICES=1 bash scripts/serve_vllm.sh vlm
```

双卡环境可使用统一管理脚本：

```bash
bash scripts/run_dual_gpu_stack.sh start
bash scripts/run_dual_gpu_stack.sh status
```

默认服务契约：

| 服务 | 地址 | 模型 |
| --- | --- | --- |
| Embedding | `http://127.0.0.1:8001/v1/embeddings` | `Qwen3-VL-Embedding-2B` |
| Reranker | `http://127.0.0.1:8002/v1/rerank` | `Qwen3-VL-Reranker-2B` |
| VLM | `http://127.0.0.1:8003/v1/chat/completions` | LoRA 适配后的 `Qwen3-VL-8B-Instruct` |

详细参数见 [vLLM 服务说明](apps/medical-vllm-service/README.md)。

### 3. Docker Compose 启动完整业务栈

```powershell
cd apps/medical-agent-java
Copy-Item .env.example .env
```

修改 `.env` 中的 MySQL、Redis、演示账号密码和三个模型服务地址，然后启动：

```powershell
docker compose config --quiet
docker compose up -d --build
```

Compose 将启动 Java 应用、MySQL、Redis、Milvus、etcd 和 MinIO；vLLM 独立运行在 GPU 主机。启动完成后访问：

- Web 页面：`http://localhost:9100`
- 健康检查：`http://localhost:9100/actuator/health`

### 4. 本地开发模式

无需启动完整基础设施即可运行测试和调试 Java 主链路。默认使用文件型 H2，并可关闭 Milvus 进入 BM25 降级模式：

```powershell
cd apps/medical-agent-java
$env:MILVUS_ENABLED='false'
$env:DEMO_USER_PASSWORD='change-user-password'
$env:DEMO_ADMIN_PASSWORD='change-admin-password'
mvn spring-boot:run
```

## API 示例

提交图文医疗咨询：

```powershell
curl.exe -u medical-user:change-user-password `
  -F "question=请结合影像与医学证据说明主要异常、局限和下一步建议" `
  -F "image=@C:\data\example.png;type=image/png" `
  http://127.0.0.1:9100/api/v1/consultations
```

管理员上传医学指南：

```powershell
curl.exe -u medical-admin:change-admin-password `
  -F "file=@C:\data\guideline.pdf;type=application/pdf" `
  http://127.0.0.1:9100/api/admin/v1/knowledge
```

管理接口：

| 接口 | 用途 |
| --- | --- |
| `GET /api/admin/v1/knowledge` | 查询知识文档与入库状态 |
| `GET /api/admin/v1/reports/{reportId}` | 查询结构化医疗报告 |
| `GET /api/admin/v1/traces/{traceId}` | 查询完整 Agent 运行轨迹 |
| `GET /api/admin/v1/skills` | 查询已加载的医疗 Skills |

## 检索与生成流程

1. 解析医学指南并进行清洗、切块和向量化，将知识元数据写入 MySQL、稠密向量写入 Milvus。
2. 根据问题和病例上下文生成原始查询与 HyDE 查询，同时执行 BM25 稀疏召回和 HNSW 稠密召回。
3. 使用 RRF 融合多路候选，再通过 Qwen3-VL Reranker 选择高相关证据。
4. 将用户影像、病例摘要、问题和证据交给 Qwen3-VL 生成回答。
5. Safety Review Agent 结合医疗 Skills 检查急症红旗、证据充分性和人工复核要求。
6. 持久化最终报告、引用证据和完整运行轨迹。

## 模型微调

项目提供 Qwen3-VL 医疗任务 LoRA 训练配置与数据处理 Notebook：

```text
training/configs/qwen3-vl-medical-lora.yaml
training/notebooks/
datasets/processed/llamafactory/
models/adapters/
```

训练流程基于 LLaMA-Factory，产出的 Adapter 由 vLLM VLM 服务加载；Java 主工程无需感知训练框架，只依赖稳定的推理接口。

## 测试

```powershell
cd apps/medical-agent-java
mvn clean verify
powershell -ExecutionPolicy Bypass -File scripts/smoke-local.ps1
```

项目包含 12 项单元与集成测试，覆盖 Spring 上下文、接口权限、病例记忆、中文分词与 BM25、文本切块、RRF、多 Agent 调度、安全复核和动态 Skills 加载。

## 安全设计

- 原始医学影像仅参与单次请求处理，不写入 MySQL、Redis、日志、报告或 trace。
- 急症红旗、低证据回答和依赖降级会触发人工复核提示。
- 报告保留证据引用和运行轨迹，支持结果回溯与责任审计。
- Basic Auth 仅用于本地演示；生产部署应接入统一身份认证、TLS 和密钥管理系统。
- 禁止将真实患者身份信息、访问令牌、模型权重和生产数据库提交到仓库。

## 文档

- [Java 应用说明](apps/medical-agent-java/README.md)
- [产品功能说明](apps/medical-agent-java/docs/PRODUCT_SPEC.md)
- [技术架构设计](apps/medical-agent-java/docs/TECH_DESIGN.md)
- [验收测试清单](apps/medical-agent-java/docs/ACCEPTANCE.md)
- [项目进度记录](docs/PROGRESS.md)
