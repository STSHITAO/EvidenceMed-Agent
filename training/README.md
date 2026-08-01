# Training

- `llamafactory/`：第三方 LlamaFactory 源码，不存放项目权重。
- `notebooks/`：下载与转换数据的 Notebook。
- `configs/`：本项目训练配置。
- `runs/legacy/`：历史失败/未产出 adapter 的训练配置和日志。

从 `training/llamafactory` 运行训练命令时，数据目录指向 `../../datasets/processed/llamafactory`，输出目录指向 `../../models/adapters`。

当前 LlamaFactory 副本有 111 个 0 字节文件，`requirements/` 下 21 个依赖清单全部为空；正式训练前必须重新获取与历史训练版本兼容的完整 LlamaFactory，不能把本目录当作已验证可运行的训练环境。
