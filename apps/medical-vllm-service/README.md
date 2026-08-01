# medical-vllm-service（EvidenceMed-VL 模型服务兼容层）

> 当前主架构中，RAG、Milvus/BM25 检索、Harness、多 Agent、MySQL、Redis、Skills 与安全审计均由 `Medical-Agent-Java` 负责。本目录保留原 EvidenceMed-VL Python 实现作为迁移对照，部署职责仅为维持原有 vLLM 推理方案：embedding `8001`、reranker `8002`、VLM `8003`。原双卡分配、模型名称和 OpenAI 兼容接口不变。

面向医疗影像辅助分析与专业知识问答场景的多模态 Medical-RAG 系统。项目支持上传医学影像与自然语言问题，联合检索医学指南文本和参考影像证据，并基于视觉语言模型生成具备循证依据的专业解答。

## 项目概览

本项目围绕“医疗影像 + 医学知识检索 + 多模态生成”构建完整链路，核心能力包括：

- 基于 `LLaMA-Factory` 对 `Qwen3-VL-8B-Instruct` 进行 `LoRA` 微调
- 使用 `Qwen3-VL-Embedding-2B` 构建图文统一向量空间
- 将医学指南文本与参考影像统一入库到 `Milvus`
- 引入 `HNSW + BM25` 的 `dense+sparse` 混合检索
- 使用 `HyDE` 作为召回增强分支，并通过 `RRF` 融合多路结果
- 使用 `Qwen3-VL-Reranker-2B` 对候选文本与参考影像进行重排
- 基于 `vLLM` 服务化部署 embedding、reranker 与 VLM 推理链路

## 系统特点

### 1. 多模态知识入库

- 文本知识：支持 `.txt`、`.md`、`.pdf`
- 影像知识：支持 `.png`、`.jpg`、`.jpeg`、`.bmp`、`.webp`、`.tif`、`.tiff`
- PDF 处理链路支持：
  - `PyMuPDF` 版面块提取
  - `pdfplumber` 表格抽取
  - `PaddleOCR` 扫描版 PDF OCR 回退
  - 页眉页脚和页码去噪
  - 标题/段落/表格级分段后再做滑窗切块

### 2. 混合检索

- 稠密检索：`Qwen3-VL-Embedding-2B + Milvus`
- 稀疏检索：`BM25`
- 召回增强：
  - 原始查询
  - 查询改写
  - 图文联合查询
  - `HyDE` 假设文档生成
- 多路结果使用 `RRF` 融合

### 3. 图文联合推理

- 使用 `Qwen3-VL-Reranker-2B` 对文本证据与参考影像进行语义重排
- 将用户影像、检索证据与问题共同输入 `Qwen3-VL`
- 输出带有证据支撑的多模态问答结果

## 目录结构

```text
apps/medical-vllm-service/
├── api.py
├── app.py
├── build_index.py
├── config/
│   ├── settings.yaml
│   ├── settings.lite.yaml
│   ├── settings.fastapi.yaml
│   └── settings.e2e.yaml
├── data/
│   └── knowledge/
│       └── images/
├── query_once.py
├── scripts/
│   ├── build_index.py
│   ├── download_qwen_models.py
│   ├── e2e_test.py
│   ├── query_once.py
│   ├── run_api.py
│   ├── run_app.py
│   ├── run_dual_gpu_stack.sh
│   ├── run_streamlit.py
│   ├── serve_vllm.sh
│   └── validate_stack.py
├── src/medical_rag/
│   ├── api_server.py
│   ├── app.py
│   ├── config.py
│   ├── pipeline.py
│   ├── prompts.py
│   ├── schemas.py
│   ├── retrieval/
│   │   ├── embedding.py
│   │   ├── reranker.py
│   │   ├── retriever.py
│   │   ├── sparse.py
│   │   └── vector_store.py
│   ├── utils/
│   │   └── text_chunker.py
│   └── vlm/
│       └── qwen_vl.py
├── streamlit_app.py
└── requirements.txt
```

## 环境准备

```bash
cd /root/autodl-tmp/EvidenceMed-Agent/apps/medical-vllm-service
pip install -r requirements.txt
```

如果需要处理扫描版 PDF，除 `paddleocr` 外，还需要安装与你环境匹配的 `paddlepaddle`。

```bash
pip install paddleocr
pip install paddlepaddle
```

## 数据组织

建议按下面方式准备知识库：

```text
data/
└── knowledge/
    ├── guideline_a.pdf
    ├── guideline_b.md
    └── images/
        ├── case_001.png
        ├── case_001.txt
        ├── case_002.jpg
        └── case_002.md
```

说明：

- 文本知识会被解析后切块入库
- 参考影像会以单张图像为知识项入库
- 若影像旁边存在同名 `.txt` 或 `.md` 文件，系统会将“图像 + 描述文本”作为混合模态知识项编码

## 下载模型

```bash
python scripts/download_qwen_models.py --cache-dir /root/autodl-tmp/Qwen
```

默认下载：

- `Qwen3-VL-Embedding-2B`
- `Qwen3-VL-Reranker-2B`

## 启动 vLLM 服务

`scripts/serve_vllm.sh` 支持三种服务模式：`embed` / `rerank` / `vlm`

```bash
# Embedding 服务，默认 8001
CUDA_VISIBLE_DEVICES=0 bash scripts/serve_vllm.sh embed

# Reranker 服务，默认 8002
CUDA_VISIBLE_DEVICES=0 bash scripts/serve_vllm.sh rerank

# VLM 服务，默认 8003
CUDA_VISIBLE_DEVICES=1 bash scripts/serve_vllm.sh vlm
```

如果是双卡，推荐使用一键脚本：

```bash
bash scripts/run_dual_gpu_stack.sh start
bash scripts/run_dual_gpu_stack.sh status
bash scripts/run_dual_gpu_stack.sh stop
```

原双卡分配保持为：GPU 0 同时承载 embedding 与 reranker，GPU 1 承载 VLM。RTX 4060 Ti 16GB 等单卡环境不要执行一键双卡脚本；如需临时调试，应使用上面的 `serve_vllm.sh` 命令一次只启动一个服务，结束后再切换下一个模型。

## 构建索引

构建知识库索引时，会同时完成：

- 文本/PDF 解析
- OCR 回退
- 文本切块
- 图像知识项构建
- 向量化写入 `Milvus`
- 稀疏语料写入本地 `BM25` 语料文件

```bash
# 标准配置
python build_index.py --config config/settings.yaml --drop-old

# Milvus Lite 本地调试
python build_index.py --config config/settings.lite.yaml --drop-old
```

## 启动系统

### Gradio

```bash
python app.py --config config/settings.yaml
```

默认地址：

- `http://0.0.0.0:7860`

### FastAPI + Streamlit

```bash
# 启动 API
python api.py --config config/settings.fastapi.yaml --host 0.0.0.0 --port 9000

# 启动 Streamlit
python scripts/run_streamlit.py --host 0.0.0.0 --port 8501 --api-base http://127.0.0.1:9000
```

访问地址：

- FastAPI 文档：`http://<server-ip>:9000/docs`
- Streamlit 页面：`http://<server-ip>:8501`

### 命令行单次问答

```bash
python query_once.py \
  --config config/settings.yaml \
  --image /path/to/image.png \
  --question "请结合影像与证据说明主要异常及建议。"
```

## 检索与推理流程

1. 解析医学指南、PDF 与参考影像，构建多模态知识库
2. 使用 `Qwen3-VL-Embedding-2B` 对文本块、影像知识项和查询进行统一编码
3. 通过 `Milvus HNSW` 与 `BM25` 同时执行 `dense+sparse` 混合检索
4. 引入原始查询、查询改写、图文联合查询和 `HyDE` 分支进行多路召回
5. 使用 `RRF` 融合多路结果
6. 使用 `Qwen3-VL-Reranker-2B` 对候选证据进行精排
7. 将用户影像、问题、文本证据和参考影像共同输入 `Qwen3-VL` 生成答案

## 验证脚本

```bash
# 检查 embedding 服务
python scripts/validate_stack.py --config config/settings.yaml --checks embed

# 检查 rerank 服务
python scripts/validate_stack.py --config config/settings.yaml --checks rerank

# 检查 milvus-lite
python scripts/validate_stack.py --config config/settings.lite.yaml --checks milvus
```

端到端测试：

```bash
python scripts/e2e_test.py \
  --api-base http://127.0.0.1:9000 \
  --image /path/to/example.png \
  --question "请结合影像和证据给出主要异常及下一步建议。" \
  --save-json /tmp/medical_rag_e2e_result.json
```

## 配置说明

常用配置文件：

- `config/settings.yaml`：标准配置
- `config/settings.lite.yaml`：本地 Milvus Lite 调试
- `config/settings.fastapi.yaml`：FastAPI + Streamlit
- `config/settings.e2e.yaml`：端到端验证

核心模块：

- `embedding.provider=api`
- `reranker.provider=api`
- `vlm.backend=vllm_openai`

## 安全声明

- 项目输出仅用于科研、教学与系统验证，不构成临床诊断结论
- 高风险病例需由具备资质的医生完成最终判读与决策
