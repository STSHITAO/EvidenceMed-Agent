# EvidenceMed-Agent 整理进度

最后更新：2026-08-01

## 已完成

- [x] 项目根目录由 `Medical_3.9` 更名为 `EvidenceMed-Agent`，并同步更新部署配置与文档路径。
- [x] 盘点根目录、目录体积和大文件。
- [x] 区分 Java 应用、vLLM 服务、训练框架、模型权重、训练数据、缓存和运行产物。
- [x] 确认本地只有 LoRA adapter/checkpoint，没有完整基座模型。
- [x] 统计训练数据完整性并记录 0 字节文件问题。
- [x] 设计统一目录和旧路径迁移表。
- [x] 阶段 1：两个应用迁入 `apps/`，LoRA adapter 与全部 checkpoint 迁入 `models/adapters/`；最终 adapter 大小 26,738,688 字节。
- [x] 阶段 2：原始图片迁入 `datasets/raw/`，转换数据迁入 `datasets/processed/`，三个 Arrow 下载缓存迁入 `datasets/cache/`；文件计数与迁移前一致。
- [x] 阶段 3：LlamaFactory、数据处理 Notebook 和旧运行缓存已分别迁入 `training/` 与 `runtime/`；根目录已收口为六个语义目录。
- [x] 阶段 4：活跃配置和 Notebook 路径已更新；历史失败训练记录、损坏 Git 元数据与临时缓存已从训练框架中分离。
- [x] 阶段 5：Notebook/JSON/YAML、vLLM Shell、Docker Compose 和 Java Maven 构建全部验证通过。
- [x] GitHub 仓库由 `EvidenceMed-VL` 更名为 `EvidenceMed-Agent`，并完成推送前的密钥与大文件检查。
- [x] 重写根目录 README，以正式项目视角说明架构、能力、部署、API、训练与安全设计。

## 验证结果

- [x] 两个 Notebook 与 `dataset_info.json` 均可正常解析。
- [x] 训练配置和四份 vLLM YAML 均可正常解析。
- [x] `serve_vllm.sh` 与 `run_dual_gpu_stack.sh` 通过 `bash -n`。
- [x] Docker Compose 配置解析通过。
- [x] `mvn clean verify`：12 个测试通过，0 失败；JAR 重新生成。

## 外部待办

- 从 Arrow 缓存或原始数据源重新生成损坏的训练 JSON 与图片。
- 在双卡 Linux GPU 环境验证真实 vLLM 和训练链路。
