# Medical-Agent-Java 技术设计

```text
Web UI / REST → Spring Security → ConsultationController → MedicalAgentHarness
                                                       ↓
                                            Dynamic Coordinator
                                                       ↓
                         ┌────────────── Shared Task Blackboard ──────────────┐
                         │ task / dependency / claim / artifact / audit event │
                         └─────────────────────────────────────────────────────┘
                                   ↓                         ↓
                     确定性 System Task                 专业 Agent Task
                     ├─ CaseMemoryService              ├─ EvidencePlanningAgent
                     ├─ AgentRuntimePreprocessor       ├─ MedicalReasoningAgent
                     ├─ JavaMedicalRagService          └─ SafetyCriticAgent
                     └─ MedicalSafetyPolicy
```

所有咨询都通过 `MedicalAgentHarness`，Controller 不直接调用模型。Java 是唯一的业务与 RAG 实现；Python 服务仅为三个无状态 vLLM 端点提供启动契约。

## 结构化 PDF 解析

PDF 入库采用 `PDF → PageProfile → DocumentElement → SectionTree → KnowledgeChunk` 五层模型，避免在解析入口丢失版面与来源信息。

```text
Upload validation / SHA-256 deduplication
                    ↓
PDFBox page preflight: text quality / image coverage / page geometry
                    ↓
Position-aware extraction ── low-quality page ──→ OcrEngine
                    ↓
Header/footer cleanup + reading-order recovery
                    ↓
Heading / paragraph / table / figure / caption classification
                    ↓
Cross-page paragraph/table/caption merge
                    ↓
Section path assignment + semantic object-aware chunking
                    ↓
MySQL locator metadata + BM25 + Milvus
```

### 解析边界

- `DocumentTextExtractor` 返回 `ParsedDocument`，而不是不可追踪的全文字符串。
- `PageProfile` 记录页宽高、文本字符数、图片数、文本层是否可靠、是否使用 OCR 及质量分。
- `DocumentElement` 记录对象类型、起止页、bbox、章节路径、正文、关联对象和解析来源。
- `PdfOcrEngine` 是可替换端口。默认关闭；启用后由 Java 渲染指定页面并调用受控 PaddleOCR HTTP 服务，禁止整本无差别 OCR。
- PDFBox 负责文本位置、字体和图片区域；表格识别使用行间距、列锚点与连续行结构启发式，并保留 Markdown 行列结果。

### 阅读顺序与清洗

页面先按 y 轴形成视觉行，再依据页面中线、列锚点和整宽对象划分阅读区域。普通单栏按 `(y, x)` 排序；双栏区域按左栏后右栏排序；标题、表格和整宽内容作为区域分隔符。页眉页脚通过跨页重复度、边缘位置和页码模式联合判断，医学单位、上下标文本和专业标点不参与激进替换。

### 跨页与章节恢复

- 上一页正文没有终止标点、下一页首对象不是标题/图注/表注时合并段落。
- 相邻表格列数一致、列锚点近似或出现重复表头时合并，并删除重复表头。
- 图像与当前页下方或下一页顶部图注按距离和编号绑定。
- 标题根据编号模式、字体相对大小和长度推断层级；对象保存完整 `sectionPath`。

### 持久化与引用

`KnowledgeChunk` 除正文外保存 `pageFrom/pageTo`、`objectType`、`sectionPath`、`boundingBoxes`、`parserVersion` 和 `qualityScore`。Embedding 文本带章节与对象语义，BM25 索引正文；返回给 Agent 的证据包含稳定 locator，从而支持“文件—章节—页码—区域”四级回溯。

## 动态编排

Coordinator 不再根据 Agent 名称排序，也不存在固定五段循环。`AgentTask` 声明任务类型、执行者类型、所需能力、依赖、优先级和轮次；黑板维护 `OPEN → CLAIMED → COMPLETED/FAILED` 状态，并把创建、认领、完成、失败和产出物摘要保存为协作事件。

每轮只释放依赖已经完成的任务。病例记忆加载与首轮证据检索互不依赖，使用四线程受控执行器并行运行；Agent 读取同一份不可变 `AgentRuntimeView`，返回不可变 `AgentResult`，最终由 Coordinator 串行合并，避免多个线程直接修改共享上下文。

能力调度依据 `AgentCapability` 和 Agent 评分选择认领者，当前能力包括：

- `EVIDENCE_PLANNING`：初次检索无结果时生成一次改写查询。
- `MEDICAL_REASONING`：生成初稿或根据安全原因修订回答。
- `SAFETY_REVIEW`：判断回答是否可发布、是否需要修订或人工复核。

典型医学请求执行图：

```text
CaseMemoryService ───────────────────────────┐
                                             ├→ MedicalReasoningAgent → SafetyCriticAgent
JavaMedicalRagService → [无证据才执行] ──────┘                         │
                         EvidencePlanningAgent                         ├→ 通过 → 输出
                                  ↓                                    └→ 拒绝 → 修订 → 再审
                         一次补充 RAG
```

简单问候跳过 RAG。急症硬规则在模型调用前短路。证据规划最多触发一次，回答最多修订两次，运行最多十二轮；仍未收敛时只返回受控人工复核说明。

## 安全决策

`AgentRuntimePreprocessor` 先执行急症红旗硬规则；命中后不调用记忆、RAG 或生成模型，直接产生 `EMERGENCY` 受控急诊提示。普通回答必须经过 `SafetyCriticAgent`。没有可引用证据时标为 `HIGH`，模型草稿会被受控人工复核说明替换；出现越权或过度确定表述时创建修订任务，而不是直接发布。

模型提示词将病例摘要、用户问题和检索结果包裹在明确的非可信边界中；系统提示词要求忽略这些内容中的指令、角色声明和安全绕过要求。原始影像和回答正文不会写入协作事件，事件只保存状态及非敏感摘要。知识库仍必须经过管理员准入与临床审核。

## 部署

vLLM 默认绑定回环地址并要求 API key。远程 GPU 应通过私有 TLS 网关暴露；Java 端通过环境变量配置三个 HTTPS 地址及相同密钥。Compose 不发布 Milvus 端口，数据库与对象存储凭据来自未提交的 `.env`。
