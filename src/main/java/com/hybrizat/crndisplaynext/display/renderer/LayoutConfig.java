package com.hybrizat.crndisplaynext.display.renderer;

import java.awt.Color;

/**
 * All visually tuneable parameters from Python _TUNEABLES_* .
 * Change numbers here to calibrate visual output — no renderer code changes needed.
 *
 * Source: pids-jre-simulator displays/train_models/e235_0/upper_lcd.py
 */
public record LayoutConfig(
    // ── Canvas ──
    int screenWidth,          // 730
    int upperHeight,          // 130

    // ── Region rects (x, y, w, h) ──
    Rect destRect,            // (0, 50, 180, 80)
    Rect prefixRect,          // (220, 3, 220, 28)
    Rect stationRect,         // (302, 41, 384, 91)
    Rect clockRect,           // (573, 0, 75, 28)
    Rect badgeRect,           // (222, 50, 68, 68)

    // ── Route ribbon ──
    int ribbonXFraction,      // × screenWidth = 0.25
    int ribbonW,              // 30
    int ribbonBottomMargin,   // 7 (height = upperHeight - this)

    // ── Destination ──
    int destFontSize,         // 35
    int destBoxX,             // 6
    int destBoxY,             // 62
    int destBoxW,             // 167

    // ── Suffix ──
    int suffixFontSize,       // 18
    String suffixText,        // "ゆき"
    int suffixRightOffset,    // 10 (distance from ribbon)
    int suffixBottomMargin,   // 5

    // ── Clock ──
    int clockFontSize,        // 31
    int clockTextOffsetY,     // -3

    // ── Prefix ──
    int prefixFontSize,       // 26
    int prefixTextOffsetY,    // 0

    // ── Station ──
    int stationFontSize,      // 64
    int stationBottomMargin,  // 5

    // ── Badge ──
    int badgeFontSize,        // 18
    int badgePaddingH,        // 16
    int badgePaddingV,        // 6

    // ── Palette ──
    Color bgColor,            // (25,25,25)
    Color fgColor,            // (230,230,230)
    Color defaultRouteColor   // (116,193,30)
) {
    /** E235-0 default config matching Python reference. */
    public static final LayoutConfig E235_0 = new LayoutConfig(
        730, 130,
        Rect.of(0, 50, 180, 80),
        Rect.of(220, 3, 220, 28),
        Rect.of(302, 41, 384, 89),
        Rect.of(573, 0, 75, 28),
        Rect.of(222, 50, 68, 68),
        25, 30, 7,
        35, 6, 62, 167,
        18, "ゆき", 10, 5,
        31, -3,
        26, 0,
        64, 5,
        18, 16, 6,
        new Color(25, 25, 25),
        new Color(230, 230, 230),
        new Color(116, 193, 30)
    );

    public record Rect(int x, int y, int w, int h) {
        public static Rect of(int x, int y, int w, int h) { return new Rect(x, y, w, h); }
    }
}
