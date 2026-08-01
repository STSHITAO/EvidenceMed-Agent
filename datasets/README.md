# Datasets

- `raw/vqa-images`：原始 VQA 图片，包含部分 0 字节文件。
- `processed/llamafactory`：LlamaFactory 格式图片和 JSON；当前 metadata JSON 不完整。
- `cache`：Hugging Face Arrow 缓存，可用于重新导出训练数据。

训练前必须先阅读 `../docs/DATA_AND_MODELS.md`，完成数据完整性修复。
