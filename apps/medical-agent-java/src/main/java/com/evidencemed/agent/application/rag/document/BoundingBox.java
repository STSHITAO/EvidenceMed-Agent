package com.evidencemed.agent.application.rag.document;

public record BoundingBox(double x, double y, double width, double height) {
    public BoundingBox {
        if (x < 0 || y < 0 || width < 0 || height < 0) {
            throw new IllegalArgumentException("bbox 坐标不能为负数");
        }
    }
}
