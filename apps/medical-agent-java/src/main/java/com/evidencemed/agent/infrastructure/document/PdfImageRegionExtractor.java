package com.evidencemed.agent.infrastructure.document;

import com.evidencemed.agent.application.rag.document.BoundingBox;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class PdfImageRegionExtractor extends PDFGraphicsStreamEngine {
    private final List<BoundingBox> regions = new ArrayList<>();
    private Point2D currentPoint;

    private PdfImageRegionExtractor(PDPage page) {
        super(page);
    }

    static List<BoundingBox> extract(PDPage page) throws IOException {
        PdfImageRegionExtractor engine = new PdfImageRegionExtractor(page);
        engine.processPage(page);
        return List.copyOf(engine.regions);
    }

    @Override
    public void drawImage(PDImage image) {
        Matrix matrix = getGraphicsState().getCurrentTransformationMatrix();
        Point2D p0 = matrix.transformPoint(0, 0);
        Point2D p1 = matrix.transformPoint(1, 0);
        Point2D p2 = matrix.transformPoint(0, 1);
        Point2D p3 = matrix.transformPoint(1, 1);
        double minX = Math.min(Math.min(p0.getX(), p1.getX()), Math.min(p2.getX(), p3.getX()));
        double maxX = Math.max(Math.max(p0.getX(), p1.getX()), Math.max(p2.getX(), p3.getX()));
        double minY = Math.min(Math.min(p0.getY(), p1.getY()), Math.min(p2.getY(), p3.getY()));
        double maxY = Math.max(Math.max(p0.getY(), p1.getY()), Math.max(p2.getY(), p3.getY()));
        double pageHeight = getPage().getMediaBox().getHeight();
        regions.add(new BoundingBox(Math.max(0, minX), Math.max(0, pageHeight - maxY),
                Math.max(0, maxX - minX), Math.max(0, maxY - minY)));
    }

    @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {}
    @Override public void clip(int windingRule) {}
    @Override public void moveTo(float x, float y) { currentPoint = new Point2D.Float(x, y); }
    @Override public void lineTo(float x, float y) { currentPoint = new Point2D.Float(x, y); }
    @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        currentPoint = new Point2D.Float(x3, y3);
    }
    @Override public Point2D getCurrentPoint() { return currentPoint; }
    @Override public void closePath() {}
    @Override public void endPath() {}
    @Override public void strokePath() {}
    @Override public void fillPath(int windingRule) {}
    @Override public void fillAndStrokePath(int windingRule) {}
    @Override public void shadingFill(COSName shadingName) {}
}
