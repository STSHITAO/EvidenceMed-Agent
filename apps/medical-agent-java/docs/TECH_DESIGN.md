# Medical-Agent-Java 技术设计

```text
Web UI / REST → Spring Security → ConsultationController → MedicalAgentHarness
                                                       ├─ CaseMemoryAgent → Redis / MySQL
                                                       ├─ EvidenceRetrieverAgent → Java RAG → Milvus / BM25 / vLLM
                                                       ├─ MedicalResponseAgent → vLLM VLM
                                                       └─ SafetyReviewAgent → Report / Trace
```

所有咨询都通过 `MedicalAgentHarness`，Controller 不直接调用模型。Java 是唯一的业务与 RAG 实现；Python 服务仅为三个无状态 vLLM 端点提供启动契约。

## 安全决策

`SafetyReviewAgent` 在生成后执行。命中急症红旗时标为 `EMERGENCY`；没有可引用证据时标为 `HIGH`。这两类结果替换为受控人工复核说明，模型草稿不会返回给用户。服务降级会标记人工复核原因。

模型提示词将病例摘要、用户问题和检索结果包裹在明确的非可信边界中；系统提示词要求忽略这些内容中的指令、角色声明和安全绕过要求。知识库仍必须经过管理员准入与临床审核。

## 部署

vLLM 默认绑定回环地址并要求 API key。远程 GPU 应通过私有 TLS 网关暴露；Java 端通过环境变量配置三个 HTTPS 地址及相同密钥。Compose 不发布 Milvus 端口，数据库与对象存储凭据来自未提交的 `.env`。
