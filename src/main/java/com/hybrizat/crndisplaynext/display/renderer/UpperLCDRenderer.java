package com.hybrizat.crndisplaynext.display.renderer;

import com.hybrizat.crndisplaynext.layout.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * E235-0 Upper LCD renderer — matches upper_lcd.py.
 * All tuneable parameters live in LayoutConfig; this class only draws.
 */
public final class UpperLCDRenderer {

    private final LayoutConfig C;
    private final JRETextLayoutEngine LAYOUT = new JRETextLayoutEngine();

    public UpperLCDRenderer(LayoutConfig config) { this.C = config; }

    /** Upper LCD data — CRN-independent. */
    public record Model(String dest, String category, Color categoryColor,
                        String prefix, String station, String clock, Color routeColor) {}

    public void render(Graphics2D g, Model m) {
        Color route = m.routeColor != null ? m.routeColor : C.defaultRouteColor();
        int sw = C.screenWidth(), uh = C.upperHeight();

        // Background
        g.setColor(C.bgColor()); g.fillRect(0, 0, sw, uh);

        // Route ribbon
        g.setColor(route);
        g.fillRect(sw * C.ribbonXFraction() / 100, 0, C.ribbonW(), uh - C.ribbonBottomMargin());

        // Destination
        LayoutConfig.Rect dr = C.destRect();
        g.setClip(dr.x(), dr.y(), dr.w(), dr.h());
        g.setColor(C.bgColor()); g.fillRect(dr.x(), dr.y(), dr.w(), dr.h());
        Font destFont = Fonts.bold(C.destFontSize());
        drawLayout(g, m.dest, destFont, C.fgColor(), C.destBoxX(), C.destBoxY(), C.destBoxW());
        // Suffix
        Font sufFont = Fonts.medium(C.suffixFontSize());
        Rectangle2D sb = sufFont.getStringBounds(C.suffixText(), g.getFontRenderContext());
        int sx = sw * C.ribbonXFraction() / 100 - (int)sb.getWidth() - C.suffixRightOffset();
        int sy = uh - (int)sb.getHeight() - C.suffixBottomMargin();
        g.setColor(C.fgColor()); g.setFont(sufFont);
        g.drawString(C.suffixText(), sx, sy + g.getFontMetrics(sufFont).getAscent());
        g.setClip(null);

        // Clock
        LayoutConfig.Rect cr = C.clockRect();
        g.setClip(cr.x(), cr.y(), cr.w(), cr.h());
        Font cf = Fonts.clock(C.clockFontSize());
        g.setFont(cf); g.setColor(C.fgColor());
        g.drawString(m.clock, cr.x(), cr.y() + C.clockTextOffsetY() + g.getFontMetrics(cf).getAscent());
        g.setClip(null);

        // Prefix
        LayoutConfig.Rect pr = C.prefixRect();
        g.setClip(pr.x(), pr.y(), pr.w(), pr.h());
        g.setColor(C.bgColor()); g.fillRect(pr.x(), pr.y(), pr.w(), pr.h());
        Font pf = Fonts.medium(C.prefixFontSize());
        g.setFont(pf); g.setColor(C.fgColor());
        g.drawString(m.prefix, pr.x(), pr.y() + C.prefixTextOffsetY() + pf.getSize());
        g.setClip(null);

        // Station (bold)
        LayoutConfig.Rect sr = C.stationRect();
        g.setClip(sr.x(), sr.y(), sr.w(), sr.h());
        g.setColor(C.bgColor()); g.fillRect(sr.x(), sr.y(), sr.w(), sr.h());
        Font sf = Fonts.bold(C.stationFontSize());
        int sH = (int)g.getFontMetrics(sf).getStringBounds(m.station, g).getHeight();
        int sY = sr.y() + sr.h() - sH - C.stationBottomMargin();
        drawLayout(g, m.station, sf, C.fgColor(), sr.x(), sY, sr.w());
        g.setClip(null);

        // Badge — only if category provided
        if (m.category != null && !m.category.isBlank()) {
            LayoutConfig.Rect br = C.badgeRect();
            g.setClip(br.x(), br.y(), br.w(), br.h());
            drawBadge(g, m.category, route, br);
            g.setClip(null);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Internal
    // ══════════════════════════════════════════════════════════════════

    private void drawLayout(Graphics2D g, String text, Font font, Color color,
                             int x, int y, int width) {
        if (text.isEmpty()) return;
        text = com.hybrizat.crndisplaynext.client.KanjiConverter.toDisplay(text, font);
        TextLayoutResult r = LAYOUT.layout(font, text, width);
        g.setFont(font); g.setColor(color);
        for (GlyphPlacement gp : r.placements())
            g.drawString(String.valueOf(gp.ch()), x + gp.x(), y + gp.y());
    }

    private void drawBadge(Graphics2D g, String name, Color routeColor, LayoutConfig.Rect br) {
        Font bf = Fonts.medium(C.badgeFontSize());
        g.setFont(bf);
        name = com.hybrizat.crndisplaynext.client.KanjiConverter.toDisplay(name, bf);
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(name) + C.badgePaddingH();
        int bh = fm.getHeight() + C.badgePaddingV();
        int bx = br.x(), by = br.y() + (br.h() - bh) / 2;
        g.setColor(Color.BLACK);
        g.fillRoundRect(bx - 3, by - 3, Math.min(bw + 6, br.w() + 6), bh + 6, 8, 8);
        g.setColor(routeColor);
        g.fillRoundRect(bx + 4, by + 4, Math.min(bw - 8, br.w() - 8), bh - 8, 4, 4);
        g.setColor(C.fgColor());
        g.fillRoundRect(bx + 6, by + 6, Math.min(bw - 12, br.w() - 12), bh - 12, 2, 2);
        g.setColor(C.bgColor());
        g.drawString(name, bx + 10, by + fm.getAscent() + 3);
    }

    /** Font loader — ShinGo (user) → MPlus (bundled) → Noto → SansSerif. */
    static final class Fonts {
        static Font medium(int size) {
            return com.hybrizat.crndisplaynext.client.FontLoader.medium(size);
        }
        static Font bold(int size) {
            return com.hybrizat.crndisplaynext.client.FontLoader.bold(size);
        }
        static Font clock(int size) {
            return com.hybrizat.crndisplaynext.client.FontLoader.clock(size);
        }
    }
}
