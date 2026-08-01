# EvidenceMed-Agent 目录设计

## 目标结构

```text
EvidenceMed-Agent/
├── apps/
│   ├── medical-agent-java/       # Java 业务主工程
│   └── medical-vllm-service/     # vLLM 推理服务与迁移参考
├── models/
│   ├── adapters/                 # LoRA adapter 与训练 checkpoint
│   └── base/                     # 基座模型挂载约定，本地不保存大模型
├── datasets/
│   ├── raw/                      # 原始图片/文本
│   ├── processed/                # LlamaFactory 格式数据
│   └── cache/                    # Hugging Face Arrow 缓存
├── training/
│   ├── llamafactory/             # 第三方训练框架
│   ├── notebooks/                # 下载、清洗和转换脚本
│   ├── configs/                  # 项目训练配置
│   └── runs/legacy/              # 历史未产出权重的训练记录
├── runtime/                      # 临时缓存和 Notebook checkpoint
├── docs/
├── AGENTS.md
└── README.md
```

## 旧路径迁移表

| 旧路径 | 新路径 | 分类 |
|---|---|---|
| `Medical-Agent-Java` | `apps/medical-agent-java` | Java 应用 |
| `Medical-vLLM-Service` | `apps/medical-vllm-service` | 模型服务 |
| `LlamaFactory` | `training/llamafactory` | 训练框架 |
| `LlamaFactory/saves/.../train_2026-01-22-16-14-48` | `models/adapters/qwen3-vl-8b-medical-lora` | LoRA 权重 |
| `VQA_data` | `datasets/raw/vqa-images` | 原始训练图片 |
| `LlamaFactory/data/medvqa_images` | `datasets/processed/llamafactory/medvqa-images` | 转换图片 |
| `LlamaFactory/data/mllm_data` | `datasets/processed/llamafactory/mllm-data` | 转换图片 |
| `MedVQA` | `datasets/cache/medvqa` | 下载缓存 |
| `cache` | `datasets/cache/med-trinity-25m` | 下载缓存 |
| `cache_medical_qa` | `datasets/cache/medical-qa` | 下载缓存 |
| 根目录 Notebook | `training/notebooks` | 数据处理工具 |
| `llamaboard_cache`、`.ipynb_checkpoints` | `runtime/legacy` | 旧运行缓存 |

路径迁移不删除任何模型或训练数据。0 字节文件也暂时保留，便于后续依据数据源重新生成。
