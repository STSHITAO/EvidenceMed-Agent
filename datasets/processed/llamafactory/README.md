# LlamaFactory processed dataset

- `medvqa-images/`：从 MedVQA Arrow 导出的图片。
- `mllm-data/`：旧多模态转换数据，存在大量 0 字节文件。
- `metadata/`：旧 JSON 元数据，目前均为 0 字节，仅作恢复线索。
- `dataset_info.json`：新的目录注册模板；在修复 `metadata/medvqa_data.json` 后供 LlamaFactory 使用。
