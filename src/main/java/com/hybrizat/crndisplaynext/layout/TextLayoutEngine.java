package com.hybrizat.crndisplaynext.layout;

import java.awt.Font;

/** Text layout without rendering — matches Python draw_text_given_width(). */
public interface TextLayoutEngine {
    TextLayoutResult layout(Font font, String text, float availableWidth);
}
