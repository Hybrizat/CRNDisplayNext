package com.hybrizat.crndisplaynext.layout;

import java.awt.Shape;

/**
 * Single glyph position — corresponds to Python's per-character placement
 * after draw_text_given_width() layout.
 *
 * @param ch       the Unicode character
 * @param x        left edge position (float, allows sub-pixel)
 * @param y        baseline offset (distance from top of layout area)
 * @param advance  glyph width for spacing calculation
 * @param outline  optional AWT Shape for rendering (may be null)
 */
public record GlyphPlacement(char ch, float x, float y, float advance, Shape outline) {}
