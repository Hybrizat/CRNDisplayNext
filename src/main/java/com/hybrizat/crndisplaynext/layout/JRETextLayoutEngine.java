package com.hybrizat.crndisplaynext.layout;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Python draw_text_given_width() — behavioral clone.
 * Uses GlyphVector.getGlyphMetrics() for accurate per-character advance.
 * Uniform spacing, supporting negative spacing for overflow.
 */
public final class JRETextLayoutEngine implements TextLayoutEngine {

    /** Lightweight Graphics2D context for font metrics (no AWT heavyweight). */
    private static final FontRenderContext FRC;
    private static final FontMetrics SAMPLE_FM;
    static {
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dummy.createGraphics();
        FRC = g.getFontRenderContext();
        SAMPLE_FM = g.getFontMetrics();
        g.dispose();
    }

    @Override
    public TextLayoutResult layout(Font font, String text, float availableWidth) {
        if (text == null || text.isEmpty())
            return new TextLayoutResult(List.of(), 0, 0, 0);

        int n = text.length();

        // Measure each character — use avg advance as floor for narrow glyphs
        float[] widths = new float[n];
        float total = 0;
        GlyphVector gv = font.createGlyphVector(FRC, text);
        for (int i = 0; i < n; i++) {
            widths[i] = gv.getGlyphMetrics(i).getAdvance();
            total += widths[i];
        }
        float avgAdvance = total / n;
        for (int i = 0; i < n; i++) {
            if (widths[i] < avgAdvance * 0.4f) {
                total += avgAdvance - widths[i];
                widths[i] = avgAdvance;
            }
        }

        // Baseline from the ACTUAL font (not SAMPLE_FM!)
        float baseline = font.getLineMetrics(text, FRC).getAscent();

        List<GlyphPlacement> placements = new ArrayList<>(n);

        if (n == 1) {
            float x = (availableWidth - widths[0]) / 2f;
            placements.add(new GlyphPlacement(text.charAt(0), x, baseline, widths[0], null));
            return new TextLayoutResult(placements, total, baseline, availableWidth);
        }

        // Uniform spacing — may be negative (compresses characters to fit)
        float spacing = (availableWidth - total) / (n + 1);
        float cx = spacing;
        for (int i = 0; i < n; i++) {
            placements.add(new GlyphPlacement(text.charAt(i), cx, baseline, widths[i], null));
            cx += widths[i] + spacing;
        }
        return new TextLayoutResult(placements, total, baseline, availableWidth);

    }
}
