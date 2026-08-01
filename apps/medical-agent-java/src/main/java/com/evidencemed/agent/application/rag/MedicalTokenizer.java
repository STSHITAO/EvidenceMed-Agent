package com.evidencemed.agent.application.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MedicalTokenizer {
    public List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isCjk(codePoint)) {
                flushLatin(tokens, latin);
                cjk.appendCodePoint(codePoint);
            } else {
                flushCjk(tokens, cjk);
                if (Character.isLetterOrDigit(codePoint)) {
                    latin.appendCodePoint(codePoint);
                } else {
                    flushLatin(tokens, latin);
                }
            }
        }
        flushLatin(tokens, latin);
        flushCjk(tokens, cjk);
        return tokens;
    }

    private void flushLatin(List<String> tokens, StringBuilder value) {
        if (!value.isEmpty()) {
            tokens.add(value.toString());
            value.setLength(0);
        }
    }

    private void flushCjk(List<String> tokens, StringBuilder value) {
        if (value.isEmpty()) {
            return;
        }
        int[] points = value.codePoints().toArray();
        if (points.length == 1) {
            tokens.add(new String(points, 0, 1));
        } else {
            for (int i = 0; i < points.length - 1; i++) {
                tokens.add(new String(points, i, 2));
            }
        }
        value.setLength(0);
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }
}
