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
