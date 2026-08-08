# 医学 PDF 结构化入库

## 目标

PDF 入库的产物是可检索、可引用、可审计的医学知识对象，而不是失去版面信息的全文字符串。解析结果保留文件、章节、页码、对象类型、坐标、解析来源和质量分，并同时进入 MySQL、BM25 与 Milvus。

## 处理流程

1. 校验文件大小、页数并通过 SHA-256 去重。
2. 使用 PDFBox 逐页读取文字坐标、字体、页面尺寸和嵌入图片区域。
3. 根据文本字符数判断文本层可靠性；可靠页面优先使用原生文本，低质量页面才渲染并调用 OCR。
4. 根据跨页重复度和页面边缘位置清除页眉、页脚与页码。
5. 将字形恢复为视觉行，识别左栏、右栏、整宽区域和连续表格行，重建阅读顺序。
6. 生成 `HEADING`、`PARAGRAPH`、`TABLE`、`FIGURE`、`CAPTION` 对象。
7. 合并跨页未结束段落和列结构一致的续表，去除重复表头，并将同页或下一页图注绑定到图片对象。
8. 根据标题编号和字体大小恢复 `sectionPath`，按章节和完整对象进行语义切块。
9. 将定位元数据写入 MySQL，正文写入 BM25，带章节与类型前缀的内容写入 Embedding/Milvus。

## 解析对象

`ParsedDocument` 包含：

- `parserVersion`：解析器版本，用于重建索引和结果对比。
- `pages`：每页尺寸、文本字符数、图片数、图片覆盖率、OCR 状态、质量分和告警。
- `elements`：页面中的结构化知识对象。
- `warnings`：例如 `PAGE_3_OCR_FALLBACK`、`PAGE_4_OCR_FAILED`。

每个 `DocumentElement` 包含：

- `type`：标题、段落、表格、图片或图注。
- `pageFrom/pageTo`：原始页码范围。
- `boundingBoxes`：PDF 页面坐标。
- `sectionPath`：恢复后的章节路径。
- `relatedElementId`：图片与图注关联。
- `extractionMethod`：`text-layer`、`ocr`、`text-table` 等。
- `qualityScore`：0 到 1 的解析质量分。

## PaddleOCR 接口

OCR 是可替换的推理端口，Java 负责页面选择、渲染、调用、坐标换算、降级和后处理。默认关闭，不会对数字原生 PDF 全量 OCR。

环境变量：

```dotenv
PADDLE_OCR_ENABLED=true
PADDLE_OCR_BASE_URL=http://127.0.0.1:8010
PADDLE_OCR_PATH=/v1/ocr
PADDLE_OCR_API_KEY=replace-with-private-token
```

请求：

```json
{
  "image_base64": "<PNG base64>",
  "page": 3
}
```

响应：

```json
{
  "qualityScore": 0.96,
  "lines": [
    {
      "text": "推荐意见：高危患者应及时评估",
      "bbox": [120, 240, 980, 64],
      "confidence": 0.97
    }
  ]
}
```

`bbox` 使用 OCR 渲染图坐标的 `[x, y, width, height]`。Java 会按页面尺寸换算为 PDF 坐标。OCR 单页失败只记录 `PAGE_n_OCR_FAILED`；其他可解析页面继续入库。整本没有任何可索引内容时才将文档标为失败。

## 知识定位

`knowledge_chunk` 保存以下检索定位字段：

```text
page_from / page_to
object_type
section_path
bounding_boxes
parser_version
quality_score
```

检索证据传给 Agent 时使用如下边界：

```xml
<evidence id="E1" source="guideline.pdf"
          section="第三章 / 影像学表现"
          pages="17-18" type="TABLE">
  ...
</evidence>
```

所有属性均执行转义，检索内容仍按非可信输入处理。

## 配置

```yaml
medical-agent:
  knowledge:
    chunk-size: 500
    chunk-overlap: 80
    pdf:
      max-pages: 500
      min-text-characters: 40
      render-dpi: 180
      ocr-enabled: false
      ocr-base-url: http://127.0.0.1:8010
      ocr-path: /v1/ocr
      ocr-timeout-seconds: 60
```

## 验证范围

自动化测试使用运行时生成的合成 PDF，覆盖重复页眉页脚、数字文本优先、OCR 回退与故障隔离、单双栏顺序、连续表格、跨页段落、跨页重复表头、图片 bbox、同页/跨页图注、章节路径和对象感知切块，不使用真实患者资料。
