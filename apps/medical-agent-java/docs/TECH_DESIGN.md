# Medical-Agent-Java 技术设计

## 架构

```text
Client -> Java API -> MedicalAgentHarness -> CoordinatorAgent
                                      |-> CaseMemoryAgent -> Redis/MySQL
                                      |-> ClinicalRouterAgent
                                      |-> EvidenceAgent -> JavaMedicalRagService
                                      |                    |-> PDFBox/TextChunker
                                      |                    |-> Java BM25
                                      |                    |-> Milvus
                                      |                    |-> vLLM embedding/reranker
                                      |                    `-> RRF + evidence assembly
                                      |-> ResponseAgent -> vLLM Qwen-VL
                                      `-> SafetyReviewAgent（最终否决/改写）
                         -> MySQL report/trace + collaboration events
```

Python 只提供无状态推理端点，不保存病例、知识或 RAG 状态。Java 是业务和检索链路的唯一事实来源。

## 代码边界

- `api`：HTTP DTO、Controller、统一异常响应。
- `application`：Harness、Agent Runtime、RAG 编排、用例服务。
- `domain`：病例、知识、证据、报告、trace 和安全决策。
- `infrastructure`：JPA、Redis、Milvus、PDFBox、vLLM HTTP 客户端、安全配置。

## RAG 链路

1. 入库：校验文件 -> PDFBox/UTF-8 提取 -> 章节感知切块 -> MySQL 写入文档和 chunk -> vLLM embedding -> Milvus upsert -> 刷新 Java BM25 快照。
2. 查询：问题规范化 -> 可选 Qwen-VL HyDE -> 文本/图文 embedding -> Milvus 稠密召回与 BM25 稀疏召回 -> RRF 去重融合 -> vLLM reranker -> 证据编号与来源组装。
3. 生成：回答 Agent 仅使用已编号证据、病例摘要和本轮影像；证据不足时明确降级。
4. 可用性：Milvus 不可用时退化为 BM25；reranker 不可用时使用 RRF 排序；Redis 不可用时回源 MySQL；VLM 不可用时返回结构化安全错误。

## 数据模型

- `user_account`：账号、密码摘要、角色、启用状态。
- `case_session` / `case_message`：病例会话与多轮文本消息，不保存影像字节。
- `knowledge_document` / `knowledge_chunk`：知识来源、哈希、解析状态、正文切块和元数据。
- `medical_report`：最终回答、风险等级、证据摘要、人工复核状态。
- `agent_run_trace` / `agent_step` / `collaboration_event`：运行、Agent 步骤和黑板事件。
- Milvus `medical_knowledge_chunks`：chunk ID、文档 ID、embedding；正文以 MySQL 为准。
- Redis `medical:case:{userId}:{sessionId}`：有 TTL 的病例摘要和最近消息。

## 安全方案

- Basic Auth 仅用于本地演示，生产密码从环境变量/外部身份系统注入。
- 限制 MIME、扩展名、文件大小和 PDF 页数；错误响应不暴露内部堆栈。
- 原始影像仅存在于单次请求内存并转为 data URL 调用 vLLM，不写入持久层和日志。
- 所有回答必须经过 SafetyReviewAgent；急症红旗、低证据、模型降级强制人工复核。
- trace 仅记录哈希、大小、MIME、耗时和决策，不记录影像或密钥。
- Web 页面与 API 都经过 Basic Auth；Controller 只调用 Harness，不直接调用任何模型客户端。
