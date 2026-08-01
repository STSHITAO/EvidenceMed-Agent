# Models

- `adapters/`：项目已经训练出的 LoRA adapter 与 checkpoint。
- `base/`：完整基座模型的挂载约定位置；当前为空。

完整 Qwen 模型体积很大，生产部署应放在独立模型盘，通过环境变量配置路径，不应复制到应用源码目录。
