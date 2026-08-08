package com.evidencemed.agent.infrastructure.document;

import com.evidencemed.agent.application.rag.document.BoundingBox;
import com.evidencemed.agent.application.rag.document.DocumentElement;
import com.evidencemed.agent.application.rag.document.DocumentElementType;
import com.evidencemed.agent.application.rag.document.ParsedDocument;
import com.evidencemed.agent.application.rag.document.PdfOcrEngine;
import com.evidencemed.agent.config.MedicalAgentProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextExtractorTest {
    @Test
    void removesRepeatedMarginsAndMergesParagraphAcrossPages() throws IOException {
        byte[] pdf = pdf(document -> {
            PDPage first = addPage(document);
            write(first, document, 50, 805, "Clinical Guideline 2026", 9);
            write(first, document, 50, 700, "This paragraph continues", 11);
            write(first, document, 290, 25, "1", 9);
            PDPage second = addPage(document);
            write(second, document, 50, 805, "Clinical Guideline 2026", 9);
            write(second, document, 50, 700, "on the next page.", 11);
            write(second, document, 290, 25, "2", 9);
        });

        ParsedDocument parsed = extractor(new DisabledOcr()).extract(pdf);

        String content = parsed.elements().stream().map(DocumentElement::content)
                .reduce((a, b) -> a + "\n" + b).orElse("");
        assertThat(content).doesNotContain("Clinical Guideline 2026");
        assertThat(content).doesNotContain("\n1\n", "\n2\n");
        assertThat(parsed.elements()).anySatisfy(element -> {
            assertThat(element.content()).contains("This paragraph continues", "on the next page.");
            assertThat(element.pageFrom()).isEqualTo(1);
            assertThat(element.pageTo()).isEqualTo(2);
        });
    }

    @Test
    void restoresTwoColumnReadingOrder() throws IOException {
        byte[] pdf = pdf(document -> {
            PDPage page = addPage(document);
            write(page, document, 50, 780, "1 Clinical Evidence Review", 14);
            write(page, document, 50, 740, "Left column first line has clinical evidence", 10);
            write(page, document, 330, 740, "Right column first line has recommendations", 10);
            write(page, document, 50, 715, "Left column second line continues the evidence", 10);
            write(page, document, 330, 715, "Right column second line closes recommendations", 10);
        });

        ParsedDocument parsed = extractor(new DisabledOcr()).extract(pdf);
        String content = parsed.elements().stream().map(DocumentElement::content)
                .reduce((a, b) -> a + "\n" + b).orElse("");

        assertThat(content.indexOf("Clinical Evidence Review")).isLessThan(content.indexOf("Left column first"));
        assertThat(content.indexOf("Left column second")).isLessThan(content.indexOf("Right column first"));
    }

    @Test
    void createsStructuredTableAndMergesRepeatedHeaderAcrossPages() throws IOException {
        byte[] pdf = pdf(document -> {
            PDPage first = addPage(document);
            tableRow(first, document, 700, "Drug", "Dose", "Unit");
            tableRow(first, document, 680, "Aspirin", "100", "mg");
            PDPage second = addPage(document);
            tableRow(second, document, 700, "Drug", "Dose", "Unit");
            tableRow(second, document, 680, "Heparin", "5000", "IU");
        });

        ParsedDocument parsed = extractor(new DisabledOcr()).extract(pdf);
        List<DocumentElement> tables = parsed.elements().stream()
                .filter(element -> element.type() == DocumentElementType.TABLE).toList();

        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).pageFrom()).isEqualTo(1);
        assertThat(tables.get(0).pageTo()).isEqualTo(2);
        assertThat(tables.get(0).content()).contains("Aspirin", "Heparin", "| Drug | Dose | Unit |");
        assertThat(count(tables.get(0).content(), "| Drug | Dose | Unit |")).isEqualTo(1);
    }

    @Test
    void invokesOcrOnlyForLowTextPage() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        PdfOcrEngine ocr = new PdfOcrEngine() {
            @Override public boolean available() { return true; }
            @Override public OcrResult recognize(BufferedImage image, int pageNumber) {
                calls.incrementAndGet();
                return new OcrResult(List.of(new OcrBlock("OCR clinical recommendation.",
                        new BoundingBox(20, 30, 200, 20), 0.92)), 0.92);
            }
        };
        byte[] pdf = pdf(document -> addPage(document));

        ParsedDocument parsed = extractor(ocr).extract(pdf);

        assertThat(calls).hasValue(1);
        assertThat(parsed.pages().get(0).ocrUsed()).isTrue();
        assertThat(parsed.elements()).anyMatch(element -> element.content().contains("OCR clinical"));
    }

    @Test
    void keepsReliableTextLayerWithoutCallingOcr() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        PdfOcrEngine ocr = new PdfOcrEngine() {
            @Override public boolean available() { return true; }
            @Override public OcrResult recognize(BufferedImage image, int pageNumber) {
                calls.incrementAndGet();
                return new OcrResult(List.of(), 0);
            }
        };
        byte[] pdf = pdf(document -> {
            PDPage page = addPage(document);
            write(page, document, 50, 700, "Digital clinical guideline text.", 11);
        });

        ParsedDocument parsed = extractor(ocr).extract(pdf);

        assertThat(calls).hasValue(0);
        assertThat(parsed.pages().get(0).textLayerReliable()).isTrue();
        assertThat(parsed.pages().get(0).ocrUsed()).isFalse();
    }

    @Test
    void recordsSinglePageOcrFailureAndKeepsExtractablePages() throws IOException {
        PdfOcrEngine ocr = new PdfOcrEngine() {
            @Override public boolean available() { return true; }
            @Override public OcrResult recognize(BufferedImage image, int pageNumber) {
                throw new IllegalStateException("synthetic OCR outage");
            }
        };
        byte[] pdf = pdf(document -> {
            addPage(document);
            PDPage second = addPage(document);
            write(second, document, 50, 700, "Extractable digital guideline evidence.", 11);
        });

        ParsedDocument parsed = extractor(ocr).extract(pdf);

        assertThat(parsed.warnings()).contains("PAGE_1_OCR_FAILED");
        assertThat(parsed.elements()).anyMatch(element ->
                element.content().contains("Extractable digital guideline evidence"));
    }

    @Test
    void assignsHeadingPathToFollowingEvidence() throws IOException {
        byte[] pdf = pdf(document -> {
            PDPage page = addPage(document);
            write(page, document, 50, 740, "2 Imaging Findings", 15);
            write(page, document, 50, 700, "The image finding requires clinical correlation.", 10);
        });

        ParsedDocument parsed = extractor(new DisabledOcr()).extract(pdf);

        assertThat(parsed.elements().stream()
                .filter(element -> element.type() == DocumentElementType.PARAGRAPH).toList())
                .singleElement().satisfies(element ->
                        assertThat(element.sectionPath()).isEqualTo("2 Imaging Findings"));
    }

    @Test
    void bindsFigureToCaption() throws IOException {
        byte[] pdf = pdf(document -> {
            PDPage page = addPage(document);
            BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 40, 40);
            graphics.setColor(Color.BLACK);
            graphics.drawLine(0, 0, 39, 39);
            graphics.dispose();
            try (PDPageContentStream stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true)) {
                stream.drawImage(LosslessFactory.createFromImage(document, image), 50, 500, 160, 160);
            }
            write(page, document, 50, 475, "Figure 1 Chest reference image", 10);
        });

        ParsedDocument parsed = extractor(new DisabledOcr()).extract(pdf);
        DocumentElement figure = parsed.elements().stream()
                .filter(element -> element.type() == DocumentElementType.FIGURE).findFirst().orElseThrow();
        DocumentElement caption = parsed.elements().stream()
                .filter(element -> element.type() == DocumentElementType.CAPTION).findFirst().orElseThrow();

        assertThat(figure.relatedElementId()).isEqualTo(caption.id());
        assertThat(caption.relatedElementId()).isEqualTo(figure.id());
        assertThat(figure.boundingBoxes()).singleElement().satisfies(box -> {
            assertThat(box.width()).isBetween(159.0, 161.0);
            assertThat(box.height()).isBetween(159.0, 161.0);
        });
        assertThat(parsed.pages().get(0).imageCoverage()).isGreaterThan(0.04);
    }

    @Test
    void bindsCaptionAtTopOfNextPageToPreviousFigure() throws IOException {
        byte[] pdf = pdf(document -> {
            PDPage first = addPage(document);
            BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
            try (PDPageContentStream stream = new PDPageContentStream(document, first,
                    PDPageContentStream.AppendMode.APPEND, true)) {
                stream.drawImage(LosslessFactory.createFromImage(document, image), 60, 100, 200, 200);
            }
            PDPage second = addPage(document);
            write(second, document, 50, 790, "Figure 2 Continued image caption", 10);
        });

        ParsedDocument parsed = extractor(new DisabledOcr()).extract(pdf);
        DocumentElement figure = parsed.elements().stream()
                .filter(element -> element.type() == DocumentElementType.FIGURE).findFirst().orElseThrow();
        DocumentElement caption = parsed.elements().stream()
                .filter(element -> element.type() == DocumentElementType.CAPTION).findFirst().orElseThrow();

        assertThat(figure.pageFrom()).isEqualTo(1);
        assertThat(caption.pageFrom()).isEqualTo(2);
        assertThat(caption.relatedElementId()).isEqualTo(figure.id());
    }

    private PdfTextExtractor extractor(PdfOcrEngine ocr) {
        MedicalAgentProperties properties = new MedicalAgentProperties();
        properties.getKnowledge().getPdf().setMinTextCharacters(1);
        return new PdfTextExtractor(ocr, properties);
    }

    private byte[] pdf(PdfWriter writer) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writer.write(document);
            document.save(output);
            return output.toByteArray();
        }
    }

    private PDPage addPage(PDDocument document) {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return page;
    }

    private void write(PDPage page, PDDocument document, float x, float y, String text, float fontSize)
            throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
            stream.newLineAtOffset(x, y);
            stream.showText(text);
            stream.endText();
        }
    }

    private void tableRow(PDPage page, PDDocument document, float y, String... cells) throws IOException {
        float[] x = {50, 230, 410};
        for (int i = 0; i < cells.length; i++) write(page, document, x[i], y, cells[i], 10);
    }

    private int count(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private interface PdfWriter { void write(PDDocument document) throws IOException; }
    private static final class DisabledOcr implements PdfOcrEngine {
        @Override public boolean available() { return false; }
        @Override public OcrResult recognize(BufferedImage image, int pageNumber) {
            throw new UnsupportedOperationException();
        }
    }
}
