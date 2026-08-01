# 数据与模型盘点

盘点日期：2026-07-29

## 模型

### 已存在

- 模型类型：Qwen3-VL-8B-Instruct 的 LoRA adapter。
- 训练目录：`models/adapters/qwen3-vl-8b-medical-lora`。
- 最终 adapter：`adapter_model.safetensors`，约 25.5 MiB。
- checkpoint：100、200、300、400、495，每个包含一份约 25 MiB adapter。
- 历史训练配置显示基座模型为 `/root/autodl-tmp/Qwen/Qwen3-VL-8B-Instruct`，数据集名为 `medvqa_data`。

### 不存在于本地

- `Qwen3-VL-8B-Instruct` 完整基座模型。
- `Qwen3-VL-Embedding-2B` 完整模型。
- `Qwen3-VL-Reranker-2B` 完整模型。
- 合并 LoRA 后的 `Qwen3-VL-8B-Instruct-merged-lora-20260308` 完整模型。

这些模型原先通过 Linux GPU 服务器上的绝对路径加载，不能把当前约 25.5 MiB 的 adapter 当作完整 8B 模型。

## 训练数据

### 原始 VQA 图片

- 文件位置：`datasets/raw/vqa-images`。
- JPG 文件：10,000 个。
- 非空 JPG：8,139 个。
- 0 字节 JPG：1,861 个。
- `mllm_data.json`：0 字节，需重新生成。

### LlamaFactory 转换数据

- `mllm-data`：8,001 个文件，其中 529 个非空、7,472 个为 0 字节。
- `medvqa-images`：104 个非空图片，约 6.86 MiB。
- 原 `medvqa_data.json`、`medical_local.json`、`dataset_info.json` 均为 0 字节。

结论：本地图片只有部分有效，训练索引 JSON 已损坏或复制不完整，当前数据不能直接复现历史训练。

## 训练框架完整性

- `training/llamafactory` 当前共有 557 个文件，其中 99 个为 0 字节；有效内容约 7.08 MiB。
- `requirements/` 下 21 个依赖清单全部为 0 字节，部分源码和示例文件同样为空。
- 历史未产出权重的训练记录已移动到 `training/runs/legacy`。
- 损坏的嵌套 `.git` 元数据已移到 `runtime/legacy/llamafactory-git-metadata`，以后可在项目根重新建仓库。

因此 LlamaFactory 目录目前只适合作为代码和历史配置参考，正式训练前应重新获取完整、版本匹配的框架。

### Hugging Face 缓存

- `datasets/cache/medvqa`：MedVQA train/test Arrow，约 35.86 MiB。
- `datasets/cache/medical-qa`：medical_llama3_instruct Arrow，约 4.21 MiB。
- `datasets/cache/med-trinity-25m`：med_trinity-25M demo Arrow，约 454.75 MiB。

缓存不是模型，也不是最终训练 JSON；它们可以用于重新导出数据。

## 建议恢复顺序

1. 从 `datasets/cache/medvqa` 重新导出 MedVQA 图片与 `medvqa_data.json`。
2. 校验每张图片能被 PIL/Java ImageIO 解码，剔除或重新下载 0 字节文件。
3. 重建独立的 LlamaFactory `dataset_info.json`，不要再修改第三方框架自带的空文件。
4. 用小样本 dry-run 验证数据格式后，再在双卡机器恢复训练。
5. 将完整基座模型放在服务器外部模型盘，通过环境变量传入，不复制进代码仓库。
