package com.evidencemed.agent.infrastructure.document;

import com.evidencemed.agent.application.rag.DocumentTextExtractor;
import com.evidencemed.agent.application.rag.document.BoundingBox;
import com.evidencemed.agent.application.rag.document.DocumentElement;
import com.evidencemed.agent.application.rag.document.DocumentElementType;
import com.evidencemed.agent.application.rag.document.PageProfile;
import com.evidencemed.agent.application.rag.document.ParsedDocument;
import com.evidencemed.agent.application.rag.document.PdfOcrEngine;
import com.evidencemed.agent.config.MedicalAgentProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PdfTextExtractor implements DocumentTextExtractor {
    public static final String PARSER_VERSION = "pdfbox-medical-v2";
    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "(?i)^(?:page\\s*)?[-–—]?\\s*\\d+\\s*[-–—]?$|^第\\s*\\d+\\s*页$");
    private static final Pattern HEADING = Pattern.compile(
            "^(?:第[一二三四五六七八九十百千0-9]+[章节部分篇条]|[0-9]+(?:\\.[0-9]+){0,4}\\s*[^0-9].*|[（(][一二三四五六七八九十0-9]+[)）].*|附录|参考文献|指南建议|推荐意见).*$");
    private static final Pattern CAPTION = Pattern.compile(
            "(?i)^(?:图|表|figure|fig\\.?|table)\\s*[-.:：]?\\s*[0-9一二三四五六七八九十A-Z].*$");
    private final PdfOcrEngine ocrEngine;
    private final MedicalAgentProperties.Pdf properties;

    public PdfTextExtractor(PdfOcrEngine ocrEngine, MedicalAgentProperties properties) {
        this.ocrEngine = ocrEngine;
        this.properties = properties.getKnowledge().getPdf();
    }

    @Override
    public boolean supports(String fileName, String mediaType) {
        return "application/pdf".equalsIgnoreCase(mediaType) || lower(fileName).endsWith(".pdf");
    }

    @Override
    public ParsedDocument extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            validatePageCount(document);
            List<PageWork> pages = analyzePages(document);
            removeRepeatedMargins(pages);
            List<DocumentElement> elements = new ArrayList<>();
            for (PageWork page : pages) elements.addAll(buildPageElements(page));
            elements = bindCaptions(mergeAcrossPages(elements));
            elements = assignSections(elements);
            boolean hasKnowledge = elements.stream().anyMatch(item -> !item.content().isBlank()
                    && item.type() != DocumentElementType.FIGURE);
            if (!hasKnowledge) {
                throw new IllegalArgumentException(ocrEngine.available()
                        ? "PDF 未解析出可索引内容" : "PDF 文本层不可用，启用 PaddleOCR 后可解析扫描页");
            }
            return new ParsedDocument(PARSER_VERSION,
                    pages.stream().map(PageWork::profile).toList(), List.copyOf(elements),
                    pages.stream().flatMap(page -> page.profile().warnings().stream()).distinct().toList());
        } catch (IOException exception) {
            throw new IllegalArgumentException("PDF 解析失败", exception);
        }
    }

    private void validatePageCount(PDDocument document) {
        int pages = document.getNumberOfPages();
        if (pages < 1) throw new IllegalArgumentException("PDF 不包含页面");
        if (pages > properties.getMaxPages()) {
            throw new IllegalArgumentException("PDF 页数不能超过 " + properties.getMaxPages() + " 页");
        }
    }

    private List<PageWork> analyzePages(PDDocument document) throws IOException {
        List<PageWork> pages = new ArrayList<>();
        PDFRenderer renderer = new PDFRenderer(document);
        for (int index = 0; index < document.getNumberOfPages(); index++) {
            PDPage page = document.getPage(index);
            int pageNumber = index + 1;
            double width = page.getMediaBox().getWidth();
            double height = page.getMediaBox().getHeight();
            List<Glyph> glyphs = new PositionStripper(pageNumber).extract(document);
            int characters = glyphs.stream().mapToInt(glyph -> glyph.text().codePointCount(0,
                    glyph.text().length())).sum();
            List<BoundingBox> imageBoxes = PdfImageRegionExtractor.extract(page);
            int imageCount = imageBoxes.size();
            double imageCoverage = imageBoxes.stream().mapToDouble(box -> box.width() * box.height())
                    .sum() / Math.max(1.0, width * height);
            boolean reliable = characters >= properties.getMinTextCharacters();
            boolean ocrUsed = false;
            List<String> warnings = new ArrayList<>();
            List<RawLine> lines;
            double quality;
            if (reliable) {
                lines = linesFromGlyphs(glyphs);
                quality = Math.min(1.0, 0.78 + Math.min(characters, 2000) / 10000.0);
            } else if (ocrEngine.available()) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(index, properties.getRenderDpi(), ImageType.RGB);
                    PdfOcrEngine.OcrResult result = ocrEngine.recognize(image, pageNumber);
                    lines = linesFromOcr(result, width, height, image.getWidth(), image.getHeight());
                    ocrUsed = true;
                    quality = result.qualityScore();
                    warnings.add("PAGE_" + pageNumber + "_OCR_FALLBACK");
                    if (lines.isEmpty()) warnings.add("PAGE_" + pageNumber + "_OCR_EMPTY");
                } catch (RuntimeException exception) {
                    lines = linesFromGlyphs(glyphs);
                    quality = characters == 0 ? 0.0 : 0.25;
                    warnings.add("PAGE_" + pageNumber + "_OCR_FAILED");
                }
            } else {
                lines = linesFromGlyphs(glyphs);
                quality = characters == 0 ? 0.0 : 0.35;
                warnings.add("PAGE_" + pageNumber + "_LOW_TEXT_OCR_UNAVAILABLE");
            }
            PageProfile profile = new PageProfile(pageNumber, width, height, characters, imageCount,
                    imageCoverage, reliable, ocrUsed, quality, warnings);
            pages.add(new PageWork(profile, new ArrayList<>(lines), imageBoxes));
        }
        return pages;
    }

    private List<RawLine> linesFromGlyphs(List<Glyph> glyphs) {
        List<Glyph> ordered = glyphs.stream().filter(glyph -> !glyph.text().isBlank())
                .sorted(Comparator.comparingDouble(Glyph::y).thenComparingDouble(Glyph::x)).toList();
        List<List<Glyph>> visualRows = new ArrayList<>();
        for (Glyph glyph : ordered) {
            List<Glyph> row = visualRows.stream()
                    .filter(candidate -> Math.abs(averageY(candidate) - glyph.y())
                            <= Math.max(2.5, glyph.height() * 0.45))
                    .min(Comparator.comparingDouble(candidate -> Math.abs(averageY(candidate) - glyph.y())))
                    .orElse(null);
            if (row == null) {
                row = new ArrayList<>();
                visualRows.add(row);
            }
            row.add(glyph);
        }
        return visualRows.stream().map(this::toRawLine).filter(line -> !line.text().isBlank())
                .sorted(Comparator.comparingDouble(line -> line.box().y())).toList();
    }

    private RawLine toRawLine(List<Glyph> row) {
        row.sort(Comparator.comparingDouble(Glyph::x));
        double averageFont = row.stream().mapToDouble(Glyph::fontSize).average().orElse(10.0);
        List<String> cells = new ArrayList<>();
        List<BoundingBox> cellBoxes = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        List<Glyph> currentGlyphs = new ArrayList<>();
        Glyph previous = null;
        for (Glyph glyph : row) {
            double gap = previous == null ? 0 : glyph.x() - (previous.x() + previous.width());
            if (previous != null && gap > Math.max(20.0, averageFont * 1.8)) {
                appendCell(cells, cellBoxes, current, currentGlyphs);
                current = new StringBuilder();
                currentGlyphs = new ArrayList<>();
            } else if (previous != null && needsWordSpace(previous, glyph, gap, averageFont)) {
                current.append(' ');
            }
            current.append(glyph.text());
            currentGlyphs.add(glyph);
            previous = glyph;
        }
        appendCell(cells, cellBoxes, current, currentGlyphs);
        BoundingBox box = union(cellBoxes);
        return new RawLine(String.join("  ", cells), box, averageFont, cells, cellBoxes, false);
    }

    private void appendCell(List<String> cells, List<BoundingBox> boxes, StringBuilder value,
            List<Glyph> glyphs) {
        String text = normalize(value.toString());
        if (text.isBlank() || glyphs.isEmpty()) return;
        cells.add(text);
        double x0 = glyphs.stream().mapToDouble(Glyph::x).min().orElse(0);
        double y0 = glyphs.stream().mapToDouble(Glyph::y).min().orElse(0);
        double x1 = glyphs.stream().mapToDouble(glyph -> glyph.x() + glyph.width()).max().orElse(x0);
        double y1 = glyphs.stream().mapToDouble(glyph -> glyph.y() + glyph.height()).max().orElse(y0);
        boxes.add(new BoundingBox(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0)));
    }

    private List<RawLine> linesFromOcr(PdfOcrEngine.OcrResult result, double pageWidth,
            double pageHeight, int imageWidth, int imageHeight) {
        double sx = pageWidth / Math.max(1, imageWidth);
        double sy = pageHeight / Math.max(1, imageHeight);
        return result.blocks().stream().filter(block -> !block.text().isBlank()).map(block -> {
            BoundingBox source = block.boundingBox();
            BoundingBox target = new BoundingBox(source.x() * sx, source.y() * sy,
                    source.width() * sx, source.height() * sy);
            return new RawLine(normalize(block.text()), target, Math.max(8, target.height()),
                    List.of(normalize(block.text())), List.of(target), false);
        }).sorted(Comparator.comparingDouble(line -> line.box().y())).toList();
    }

    private void removeRepeatedMargins(List<PageWork> pages) {
        if (pages.isEmpty()) return;
        Map<String, Integer> frequency = new HashMap<>();
        for (PageWork page : pages) {
            Set<String> seen = new LinkedHashSet<>();
            for (RawLine line : page.lines()) {
                if (isMargin(line, page.profile().height()) && line.text().length() <= 120) {
                    seen.add(edgeKey(line.text()));
                }
            }
            seen.forEach(key -> frequency.merge(key, 1, Integer::sum));
        }
        int repeatThreshold = Math.max(2, (int) Math.ceil(pages.size() * 0.4));
        for (PageWork page : pages) {
            page.lines().removeIf(line -> PAGE_NUMBER.matcher(line.text().strip()).matches()
                    || (isMargin(line, page.profile().height())
                    && frequency.getOrDefault(edgeKey(line.text()), 0) >= repeatThreshold));
        }
    }

    private List<DocumentElement> buildPageElements(PageWork page) {
        List<RawLine> raw = markTableRows(page.lines());
        List<VisualLine> ordered = restoreReadingOrder(raw, page.profile().width());
        double medianFont = median(ordered.stream().filter(line -> !line.tableRow())
                .mapToDouble(VisualLine::fontSize).toArray());
        List<DocumentElement> result = new ArrayList<>();
        List<VisualLine> paragraph = new ArrayList<>();
        int sequence = 0;
        for (int i = 0; i < ordered.size();) {
            VisualLine line = ordered.get(i);
            if (line.tableRow()) {
                sequence = flushParagraph(result, paragraph, page, medianFont, sequence);
                List<VisualLine> rows = new ArrayList<>();
                while (i < ordered.size() && ordered.get(i).tableRow()) rows.add(ordered.get(i++));
                result.add(tableElement(page, rows, sequence++));
                continue;
            }
            DocumentElementType type = classify(line, medianFont);
            if (type == DocumentElementType.HEADING || type == DocumentElementType.CAPTION) {
                sequence = flushParagraph(result, paragraph, page, medianFont, sequence);
                result.add(lineElement(page, line, type, sequence++, medianFont));
            } else if (!paragraph.isEmpty() && shouldStartParagraph(paragraph.get(paragraph.size() - 1), line)) {
                sequence = flushParagraph(result, paragraph, page, medianFont, sequence);
                paragraph.add(line);
            } else {
                paragraph.add(line);
            }
            i++;
        }
        flushParagraph(result, paragraph, page, medianFont, sequence);
        for (int i = 0; i < page.imageBoxes().size(); i++) {
            result.add(new DocumentElement("p" + page.profile().pageNumber() + "-figure-" + (i + 1),
                    DocumentElementType.FIGURE, page.profile().pageNumber(), page.profile().pageNumber(),
                    List.of(page.imageBoxes().get(i)), "", "", "", "pdf-image",
                    page.profile().qualityScore(), 0));
        }
        return result;
    }

    private List<RawLine> markTableRows(List<RawLine> source) {
        List<RawLine> lines = source.stream().sorted(Comparator.comparingDouble(line -> line.box().y())).toList();
        Set<Integer> tableIndexes = new HashSet<>();
        int start = 0;
        while (start < lines.size()) {
            if (lines.get(start).cells().size() < 2) { start++; continue; }
            int end = start + 1;
            int columns = lines.get(start).cells().size();
            while (end < lines.size() && lines.get(end).cells().size() == columns
                    && lines.get(end).box().y() - bottom(lines.get(end - 1).box()) < 24) end++;
            if (end - start >= 2 && (columns >= 3 || tableLike(lines.subList(start, end)))) {
                for (int i = start; i < end; i++) tableIndexes.add(i);
            }
            start = Math.max(end, start + 1);
        }
        List<RawLine> marked = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            RawLine line = lines.get(i);
            marked.add(new RawLine(line.text(), line.box(), line.fontSize(), line.cells(),
                    line.cellBoxes(), tableIndexes.contains(i)));
        }
        return marked;
    }

    private boolean tableLike(List<RawLine> rows) {
        double averageCellLength = rows.stream().flatMap(row -> row.cells().stream())
                .mapToInt(String::length).average().orElse(100);
        return averageCellLength <= 24;
    }

    private List<VisualLine> restoreReadingOrder(List<RawLine> lines, double pageWidth) {
        double middle = pageWidth / 2.0;
        long splitRows = lines.stream().filter(line -> !line.tableRow() && line.cells().size() >= 2
                && line.cellBoxes().get(0).x() < middle
                && line.cellBoxes().get(line.cellBoxes().size() - 1).x() > middle
                && line.cells().stream().mapToInt(String::length).sum() >= 20).count();
        boolean twoColumns = splitRows >= 2;
        List<VisualLine> visual = new ArrayList<>();
        for (RawLine line : lines) {
            if (line.tableRow()) {
                visual.add(new VisualLine(line.text(), line.box(), line.fontSize(), line.cells(),
                        line.cellBoxes(), true, 2));
            } else if (twoColumns && line.cells().size() >= 2) {
                for (int i = 0; i < line.cells().size(); i++) {
                    BoundingBox box = line.cellBoxes().get(i);
                    int column = box.x() + box.width() / 2 < middle ? 0 : 1;
                    visual.add(new VisualLine(line.cells().get(i), box, line.fontSize(),
                            List.of(line.cells().get(i)), List.of(box), false, column));
                }
            } else {
                int column = !twoColumns || line.box().width() > pageWidth * 0.62
                        || HEADING.matcher(line.text().strip()).matches() ? 2
                        : line.box().x() + line.box().width() / 2 < middle ? 0 : 1;
                visual.add(new VisualLine(line.text(), line.box(), line.fontSize(), line.cells(),
                        line.cellBoxes(), false, column));
            }
        }
        if (!twoColumns) {
            return visual.stream().sorted(Comparator.comparingDouble((VisualLine line) -> line.box().y())
                    .thenComparingDouble(line -> line.box().x())).toList();
        }
        List<VisualLine> result = new ArrayList<>();
        List<VisualLine> separators = visual.stream().filter(line -> line.column() == 2)
                .sorted(Comparator.comparingDouble(line -> line.box().y())).toList();
        double start = Double.NEGATIVE_INFINITY;
        for (VisualLine separator : separators) {
            addColumnBand(result, visual, start, separator.box().y());
            result.add(separator);
            start = separator.box().y() + 0.01;
        }
        addColumnBand(result, visual, start, Double.POSITIVE_INFINITY);
        return result;
    }

    private void addColumnBand(List<VisualLine> target, List<VisualLine> all, double from, double to) {
        for (int column = 0; column <= 1; column++) {
            int selected = column;
            all.stream().filter(line -> line.column() == selected && line.box().y() >= from
                    && line.box().y() < to).sorted(Comparator.comparingDouble(line -> line.box().y()))
                    .forEach(target::add);
        }
    }

    private int flushParagraph(List<DocumentElement> result, List<VisualLine> lines, PageWork page,
            double medianFont, int sequence) {
        if (lines.isEmpty()) return sequence;
        StringBuilder text = new StringBuilder();
        for (VisualLine line : lines) appendSemantic(text, line.text());
        List<BoundingBox> boxes = lines.stream().map(VisualLine::box).toList();
        result.add(new DocumentElement("p" + page.profile().pageNumber() + "-paragraph-" + sequence,
                DocumentElementType.PARAGRAPH, page.profile().pageNumber(), page.profile().pageNumber(),
                boxes, text.toString(), "", "", page.profile().ocrUsed() ? "ocr" : "text-layer",
                page.profile().qualityScore(), 0));
        lines.clear();
        return sequence + 1;
    }

    private DocumentElement lineElement(PageWork page, VisualLine line, DocumentElementType type,
            int sequence, double medianFont) {
        int level = type == DocumentElementType.HEADING ? headingLevel(line.text(), line.fontSize(), medianFont) : 0;
        return new DocumentElement("p" + page.profile().pageNumber() + "-" + type.name().toLowerCase(Locale.ROOT)
                + "-" + sequence, type, page.profile().pageNumber(), page.profile().pageNumber(),
                List.of(line.box()), line.text(), "", "", page.profile().ocrUsed() ? "ocr" : "text-layer",
                page.profile().qualityScore(), level);
    }

    private DocumentElement tableElement(PageWork page, List<VisualLine> rows, int sequence) {
        List<String> markdownRows = rows.stream().map(row -> "| " + row.cells().stream()
                .map(value -> value.replace("|", "\\|")).reduce((a, b) -> a + " | " + b).orElse("") + " |").toList();
        StringBuilder markdown = new StringBuilder(markdownRows.get(0)).append('\n');
        markdown.append("| ").append("--- | ".repeat(rows.get(0).cells().size())).append('\n');
        for (int i = 1; i < markdownRows.size(); i++) markdown.append(markdownRows.get(i)).append('\n');
        return new DocumentElement("p" + page.profile().pageNumber() + "-table-" + sequence,
                DocumentElementType.TABLE, page.profile().pageNumber(), page.profile().pageNumber(),
                rows.stream().map(VisualLine::box).toList(), markdown.toString().strip(), "", "",
                page.profile().ocrUsed() ? "ocr-table" : "text-table", page.profile().qualityScore(), 0);
    }

    private List<DocumentElement> mergeAcrossPages(List<DocumentElement> source) {
        List<DocumentElement> merged = new ArrayList<>();
        for (DocumentElement current : source) {
            if (!merged.isEmpty()) {
                DocumentElement previous = merged.get(merged.size() - 1);
                if (canMergeParagraph(previous, current)) {
                    merged.set(merged.size() - 1, previous.merge(current,
                            joinText(previous.content(), current.content())));
                    continue;
                }
                if (canMergeTable(previous, current)) {
                    merged.set(merged.size() - 1, previous.merge(current,
                            mergeTableMarkdown(previous.content(), current.content())));
                    continue;
                }
            }
            merged.add(current);
        }
        return merged;
    }

    private List<DocumentElement> bindCaptions(List<DocumentElement> source) {
        List<DocumentElement> result = new ArrayList<>(source);
        for (int i = 0; i < result.size(); i++) {
            DocumentElement caption = result.get(i);
            if (caption.type() != DocumentElementType.CAPTION) continue;
            int figureIndex = nearestFigure(result, caption);
            if (figureIndex >= 0) {
                DocumentElement figure = result.get(figureIndex);
                result.set(i, caption.withRelation(figure.id()));
                result.set(figureIndex, figure.withRelation(caption.id()));
            }
        }
        return result;
    }

    private int nearestFigure(List<DocumentElement> elements, DocumentElement caption) {
        int found = -1;
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < elements.size(); i++) {
            DocumentElement candidate = elements.get(i);
            if (candidate.type() != DocumentElementType.FIGURE || !candidate.relatedElementId().isBlank()) continue;
            int pageDistance = caption.pageFrom() - candidate.pageTo();
            double spatial = verticalDistance(candidate, caption);
            double score = pageDistance * 10000.0 + spatial;
            if (pageDistance >= 0 && pageDistance <= 1 && score < distance) {
                distance = score;
                found = i;
            }
        }
        return found;
    }

    private double verticalDistance(DocumentElement figure, DocumentElement caption) {
        if (figure.boundingBoxes().isEmpty() || caption.boundingBoxes().isEmpty()) return 1000.0;
        BoundingBox image = figure.boundingBoxes().get(0);
        BoundingBox text = caption.boundingBoxes().get(0);
        return Math.abs(text.y() - (image.y() + image.height()));
    }

    private List<DocumentElement> assignSections(List<DocumentElement> source) {
        String[] levels = new String[6];
        List<DocumentElement> result = new ArrayList<>();
        for (DocumentElement element : source) {
            if (element.type() == DocumentElementType.HEADING) {
                int level = Math.max(1, element.headingLevel());
                levels[level - 1] = element.content();
                Arrays.fill(levels, level, levels.length, null);
            }
            String path = Arrays.stream(levels).filter(value -> value != null && !value.isBlank())
                    .reduce((a, b) -> a + " / " + b).orElse("");
            result.add(element.withSectionPath(path));
        }
        return result;
    }

    private boolean canMergeParagraph(DocumentElement previous, DocumentElement current) {
        return previous.type() == DocumentElementType.PARAGRAPH
                && current.type() == DocumentElementType.PARAGRAPH
                && current.pageFrom() == previous.pageTo() + 1
                && !endsSentence(previous.content());
    }

    private boolean canMergeTable(DocumentElement previous, DocumentElement current) {
        return previous.type() == DocumentElementType.TABLE && current.type() == DocumentElementType.TABLE
                && current.pageFrom() == previous.pageTo() + 1
                && tableColumns(previous.content()) == tableColumns(current.content());
    }

    private String mergeTableMarkdown(String first, String second) {
        List<String> left = first.lines().toList();
        List<String> right = new ArrayList<>(second.lines().toList());
        if (right.size() >= 2) {
            if (!left.isEmpty() && normalizeTableRow(left.get(0)).equals(normalizeTableRow(right.get(0)))) {
                right = right.subList(2, right.size());
            } else {
                right = right.subList(2, right.size());
            }
        }
        return first.strip() + "\n" + String.join("\n", right);
    }

    private String normalizeTableRow(String value) { return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private int tableColumns(String markdown) { return Math.max(0, (int) markdown.lines().findFirst().orElse("").chars().filter(c -> c == '|').count() - 1); }
    private boolean endsSentence(String value) { return value.matches("(?s).*[。！？.!?；;]$"); }

    private DocumentElementType classify(VisualLine line, double medianFont) {
        String value = line.text().strip();
        if (CAPTION.matcher(value).matches() && value.length() <= 160) return DocumentElementType.CAPTION;
        if (value.length() <= 120 && (HEADING.matcher(value).matches()
                || line.fontSize() >= Math.max(11, medianFont * 1.18))) return DocumentElementType.HEADING;
        return DocumentElementType.PARAGRAPH;
    }

    private int headingLevel(String value, double fontSize, double medianFont) {
        if (value.matches("^第.+[章篇].*")) return 1;
        java.util.regex.Matcher matcher = Pattern.compile("^([0-9]+(?:\\.[0-9]+){0,4})").matcher(value);
        if (matcher.find()) return Math.min(6, matcher.group(1).split("\\.").length);
        return fontSize >= medianFont * 1.45 ? 1 : 2;
    }

    private boolean shouldStartParagraph(VisualLine previous, VisualLine current) {
        if (previous.column() != current.column()) return true;
        double gap = current.box().y() - bottom(previous.box());
        return gap > Math.max(8.0, previous.fontSize() * 1.4);
    }

    private boolean needsWordSpace(Glyph previous, Glyph current, double gap, double font) {
        if (gap <= Math.max(1.2, font * 0.18)) return false;
        int left = previous.text().codePointBefore(previous.text().length());
        int right = current.text().codePointAt(0);
        return isLatinOrDigit(left) && isLatinOrDigit(right);
    }

    private boolean isLatinOrDigit(int value) { return value < 128 && Character.isLetterOrDigit(value); }

    private void appendSemantic(StringBuilder target, String value) {
        String next = normalize(value);
        if (next.isBlank()) return;
        if (target.isEmpty()) { target.append(next); return; }
        char last = target.charAt(target.length() - 1);
        char first = next.charAt(0);
        if (last == '-') target.deleteCharAt(target.length() - 1);
        else if (isAsciiWord(last) && isAsciiWord(first)) target.append(' ');
        target.append(next);
    }

    private String joinText(String first, String second) {
        StringBuilder value = new StringBuilder(first);
        appendSemantic(value, second);
        return value.toString();
    }

    private boolean isAsciiWord(char value) { return value < 128 && Character.isLetterOrDigit(value); }

    private boolean isMargin(RawLine line, double pageHeight) {
        return line.box().y() <= pageHeight * 0.12 || bottom(line.box()) >= pageHeight * 0.88;
    }

    private String edgeKey(String value) {
        return normalize(value).toLowerCase(Locale.ROOT).replaceAll("\\d+", "#");
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u3000', ' ').replace('\u00a0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ").replaceAll(" ?\\n ?", "\n").strip();
    }

    private double averageY(List<Glyph> glyphs) { return glyphs.stream().mapToDouble(Glyph::y).average().orElse(0); }
    private double bottom(BoundingBox box) { return box.y() + box.height(); }
    private double median(double[] values) {
        if (values.length == 0) return 10.0;
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private BoundingBox union(List<BoundingBox> boxes) {
        if (boxes.isEmpty()) return new BoundingBox(0, 0, 0, 0);
        double x0 = boxes.stream().mapToDouble(BoundingBox::x).min().orElse(0);
        double y0 = boxes.stream().mapToDouble(BoundingBox::y).min().orElse(0);
        double x1 = boxes.stream().mapToDouble(box -> box.x() + box.width()).max().orElse(x0);
        double y1 = boxes.stream().mapToDouble(box -> box.y() + box.height()).max().orElse(y0);
        return new BoundingBox(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    private String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }

    private static final class PositionStripper extends PDFTextStripper {
        private final int pageNumber;
        private final List<Glyph> glyphs = new ArrayList<>();

        private PositionStripper(int pageNumber) throws IOException {
            this.pageNumber = pageNumber;
            setSortByPosition(true);
            setStartPage(pageNumber);
            setEndPage(pageNumber);
        }

        private List<Glyph> extract(PDDocument document) throws IOException {
            getText(document);
            return List.copyOf(glyphs);
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            String value = text.getUnicode();
            if (value != null && !value.isBlank()) {
                glyphs.add(new Glyph(value, text.getXDirAdj(), text.getYDirAdj(),
                        Math.max(0, text.getWidthDirAdj()), Math.max(0, text.getHeightDir()),
                        Math.max(1, text.getFontSizeInPt())));
            }
            super.processTextPosition(text);
        }
    }

    private record Glyph(String text, double x, double y, double width, double height, double fontSize) {}
    private record RawLine(String text, BoundingBox box, double fontSize, List<String> cells,
                           List<BoundingBox> cellBoxes, boolean tableRow) {}
    private record VisualLine(String text, BoundingBox box, double fontSize, List<String> cells,
                              List<BoundingBox> cellBoxes, boolean tableRow, int column) {}
    private record PageWork(PageProfile profile, List<RawLine> lines, List<BoundingBox> imageBoxes) {}
}
