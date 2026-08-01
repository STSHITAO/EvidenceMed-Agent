package com.evidencemed.agent.infrastructure.document;

import com.evidencemed.agent.application.rag.DocumentTextExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor implements DocumentTextExtractor {
    @Override
    public boolean supports(String fileName, String mediaType) {
        return "application/pdf".equalsIgnoreCase(mediaType) || lower(fileName).endsWith(".pdf");
    }

    @Override
    public String extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() > 500) {
                throw new IllegalArgumentException("PDF 页数不能超过 500 页");
            }
            String text = new PDFTextStripper().getText(document);
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("PDF 未包含可提取文本，扫描版文件需要先执行 OCR");
            }
            return text;
        } catch (IOException exception) {
            throw new IllegalArgumentException("PDF 解析失败", exception);
        }
    }

    private String lower(String value) { return value == null ? "" : value.toLowerCase(); }
}
