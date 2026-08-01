SYSTEM_PROMPT = """
你是医疗影像辅助分析系统中的专业助手。请严格依据输入影像与检索到的医学证据进行回答，禁止编造指南、文献或诊断结论。
请按以下结构输出：
1. 影像所见与初步分析
2. 结合检索证据的医学解释
3. 风险提示与下一步建议
如果证据不足，请明确说明“不足以支持确定性结论”。
""".strip()


def build_user_prompt(question: str, evidence_blocks: list[str], evidence_image_count: int = 0) -> str:
    evidence_text = "\n\n".join(
        [f"[证据{i + 1}]\n{block}" for i, block in enumerate(evidence_blocks)]
    )
    image_hint = ""
    if evidence_image_count > 0:
        image_hint = (
            f"\n补充说明：第1张图像是用户上传的待分析影像，后续额外提供的 {evidence_image_count} 张图像为检索到的参考影像，"
            "顺序与证据列表中标注为图像证据的条目一致。"
        )
    return (
        f"用户问题：{question}\n\n"
        f"可用医学证据如下，请在回答中显式参考其要点：\n{evidence_text}\n"
        f"{image_hint}\n\n"
        "请基于影像与证据进行联合推理，并给出专业回答。"
    )


def build_hyde_prompt(question: str) -> str:
    return (
        f"用户问题：{question}\n\n"
        "请生成一段用于检索的假设性医学证据摘要。"
        "要求："
        "1. 用 4 到 6 句医学风格文本描述可能的影像征象、相关疾病线索、检查建议和指南关键词；"
        "2. 不要写成列表；"
        "3. 不要提及你在做假设；"
        "4. 输出适合拿去做语义检索的连贯段落。"
    )
