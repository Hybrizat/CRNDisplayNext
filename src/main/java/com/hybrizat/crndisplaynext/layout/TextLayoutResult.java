package com.hybrizat.crndisplaynext.layout;

import java.util.List;

/**
 * Result of text layout — a list of positioned glyphs plus metadata.
 *
 * @param placements    glyph positions (left-to-right order)
 * @param totalWidth    sum of all glyph advances before spacing
 * @param baseline      font ascent (distance from top to baseline)
 * @param usedWidth     actual width consumed (may differ from availableWidth)
 */
public record TextLayoutResult(List<GlyphPlacement> placements,
                                float totalWidth, float baseline, float usedWidth) {
    public boolean isEmpty() { return placements.isEmpty(); }
}
