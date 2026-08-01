# EvidenceMed-Agent

多模态医疗问答与咨询项目。Java 是业务主链路，Python/vLLM 只负责模型推理，LlamaFactory 仅用于离线 LoRA 训练。

## 先看这里

| 目录 | 内容 | 是否运行时必需 |
|---|---|---|
| `apps/medical-agent-java` | Java RAG、Harness、多 Agent、MySQL、Redis、Milvus、Skills | 是 |
| `apps/medical-vllm-service` | 原 vLLM 三服务启动与迁移参考 | GPU 部署时需要 |
| `models/adapters` | 已训练的 LoRA adapter 和 checkpoint | 推理/继续训练时需要 |
| `models/base` | 基座模型约定位置；当前本地为空 | 需单独下载或挂载 |
| `datasets/raw` | 原始训练图片 | 训练时需要 |
| `datasets/processed` | 转换后的 LlamaFactory 数据 | 训练时需要，目前不完整 |
| `datasets/cache` | Hugging Face Arrow 下载缓存 | 可重新下载 |
| `training/llamafactory` | 第三方训练框架源码 | 训练时需要 |
| `training/notebooks` | 数据下载与预处理 Notebook | 数据准备时需要 |
| `runtime` | Notebook/LlamaBoard 临时文件 | 可重新生成 |

## 模型在哪里

本地没有完整的 `Qwen3-VL-8B-Instruct` 基座模型。当前唯一可确认的训练权重位于：

```text
models/adapters/qwen3-vl-8b-medical-lora/
├── adapter_model.safetensors
├── adapter_config.json
├── checkpoint-100/
├── checkpoint-200/
├── checkpoint-300/
├── checkpoint-400/
└── checkpoint-495/
```

基座模型、Embedding 模型和 Reranker 模型原部署在 Linux GPU 机器的 `/root/autodl-tmp/Qwen/`，不在当前 Windows 项目目录中。

## 训练数据在哪里

- 原始 VQA 图片：`datasets/raw/vqa-images`
- LlamaFactory 转换数据：`datasets/processed/llamafactory`
- Hugging Face 下载缓存：`datasets/cache`

当前数据存在 0 字节文件和缺失 JSON，不能直接宣称可复现训练。详细清单见 [数据与模型盘点](docs/DATA_AND_MODELS.md)。

## 启动入口

Java 主工程：

```bash
cd apps/medical-agent-java
mvn spring-boot:run
```

双卡 vLLM 服务：

```bash
cd apps/medical-vllm-service
bash scripts/run_dual_gpu_stack.sh start
```

RTX 4060 Ti 16GB 本地环境不要启动双卡脚本；如需调试，只能按需一次启动一个 vLLM 服务。
