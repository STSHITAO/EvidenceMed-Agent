# Model adapters

`qwen3-vl-8b-medical-lora/` 是当前医疗多模态 LoRA 训练产物，包括最终 adapter 和 checkpoint-100 至 checkpoint-495。

`adapter_model.safetensors` 只有约 25.5 MiB，它不是完整模型；推理前必须与 Qwen3-VL-8B-Instruct 基座模型合并，或由支持 PEFT 的运行时同时加载。
