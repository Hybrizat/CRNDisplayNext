package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import com.hybrizat.crndisplaynext.client.DynamicTextureHolder;
import com.hybrizat.crndisplaynext.client.FontLoader;
import com.hybrizat.crndisplaynext.display.renderer.UpperLCDRenderer;
import com.hybrizat.crndisplaynext.display.renderer.LayoutConfig;
import com.hybrizat.crndisplaynext.display.settings.JREVISSettings;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.crn.config.ModClientConfig;
import de.mrjulsen.crn.data.train.ETrainStopState;
import de.mrjulsen.crn.data.train.portable.TrainDisplayData;
import de.mrjulsen.crn.data.train.portable.TrainStopDisplayData;
import de.mrjulsen.crn.util.ModUtils;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.util.RenderUtils;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.time.DLTime;
import de.mrjulsen.mcdragonlib.util.time.TimeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * JRE Passenger VIS — E235-0 replica.
 * Coordinates the canvas (730×420 → DynamicTexture) and delegates
 * drawing to UpperLCDRenderer / LowerLCD (inline for now).
 */
public class BERJREPassengerVIS implements AbstractAdvancedDisplayRenderer<JREVISSettings> {

    private static final UpperLCDRenderer UPPER = new UpperLCDRenderer(LayoutConfig.E235_0);

    // Canvas (e235_0/__init__.py)
    private static final int SW = 730, SH = 420, UH = 130;
    // Lower LCD (constants.py STOPS_*)
    private static final int STOPS_PER_LINE = 14;
    private static final int STOPS_WIDTH = 42;
    private static final int STOPS_BAR_HEIGHT = 30;
    private static final Color DARK_BG = new Color(25, 25, 25);
    private static final Color WHITE_BG = new Color(230, 230, 230);
    private static final Color INACTIVE = new Color(110, 110, 110);
    private static final Color CURRENT_COLOR = new Color(226, 180, 25);
    private static final Color GREEN = new Color(116, 193, 30);
    private static final Color RED = new Color(224, 54, 37);

    private static final int FONT_STOPS = 22, FONT_TIME = 14;

    enum Page { FULL_ROUTE, FIVE_STATION }
    private static final Map<Long, DynamicTextureHolder> H = new HashMap<>();
    private Page page = Page.FULL_ROUTE;
    private long pageStart;
    private int cw = 16, ch = 16;

    @Override public void tick(Level lv, BlockPos pos, BlockState st,
                                AdvancedDisplayBlockEntity be, AdvancedDisplayRenderInstance p) {
        if (!be.isController()) return;
        long t = ModUtils.getTransformedWorldTime();
        if (t - pageStart >= getDisplaySettings(be).getPageIntervalSecs() * 20L) {
            page = (page == Page.FULL_ROUTE) ? Page.FIVE_STATION : Page.FULL_ROUTE;
            pageStart = t;
        }
    }

    @Override public void render(BERGraphics<AdvancedDisplayBlockEntity> g, float pt,
                                  AdvancedDisplayRenderInstance p, int l, boolean bs) {
        var be = g.blockEntity();
        if (!be.isController()) return;
        var holder = H.get(be.getBlockPos().asLong());
        if (holder == null || !holder.isReady()) return;
        RenderUtils.renderTexture(holder.getId(), g,
            new Vector3f(2, 2, 0.02f), cw - 4, ch - 4,
            0, 0, 1, 1,
            be.getBlockState().getValue(HorizontalDirectionalBlock.FACING),
            DLColor.WHITE, false);
    }

    @Override public void update(Level lv, BlockPos pos, BlockState st,
                                  AdvancedDisplayBlockEntity be,
                                  AdvancedDisplayRenderInstance p, EUpdateReason r) {
        if (!be.isController()) return;
        var cfg = getDisplaySettings(be);
        cw = be.getXSizeScaled() * 16;
        ch = be.getYSizeScaled() * 16;
        long k = be.getBlockPos().asLong();
        var holder = H.computeIfAbsent(k, _k -> new DynamicTextureHolder(SW, SH));
        holder.resize(SW, SH, null);
        BufferedImage img = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        try { draw(g2, be, cfg, lv); } catch (Exception e) { CRNDisplayNextMod.LOGGER.error("[VIS]", e); }
        g2.dispose();
        holder.copyFromBuffered(img);
        holder.upload();
        holder.markReady();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Master draw — delegates upper to UpperLCDRenderer, keeps lower inline
    // ═══════════════════════════════════════════════════════════════════════════

    private void draw(Graphics2D g, AdvancedDisplayBlockEntity be, JREVISSettings cfg, Level lv) {
        g.setColor(DARK_BG); g.fillRect(0, 0, SW, SH);
        g.setColor(WHITE_BG); g.fillRect(0, UH, SW, SH - UH);
        TrainDisplayData d = be.getTrainData();
        if (d == null || d.getState().isIrregular(cfg.showDoNotBoardText())) {
            oos(g, d != null && d.getState().shouldNotBoard(false)); return;
        }
        List<TrainStopDisplayData> stops = d.getStopsFromCurrentStation();
        if (stops.isEmpty()) return;

        // Build model from CRN data
        var ss = ETrainStopState.beforeArrival(!d.isWaitingAtStation());
        Color routeColor = GREEN;
        String cat = "";
        if (d.getTrainData() instanceof IBasicTrainDisplayDataExt ext) {
            cat = ext.crndisplaynext$getCategoryName(ss);
            DLColor cc = ext.crndisplaynext$getCategoryColor(ss);
            if (cc != null && !cc.isTransparent()) routeColor = new Color(cc.getRed(), cc.getGreen(), cc.getBlue());
        }
        if (cat.isBlank()) {
            cat = d.getTrainData().getName(ss);
            DLColor lc = d.getTrainData().getColor(ss);
            if (lc != null && !lc.isTransparent()) routeColor = new Color(lc.getRed(), lc.getGreen(), lc.getBlue());
        }
        String timeStr = new DLTime(lv, DLTime.defaultTimeSystem())
            .format(ModClientConfig.TIME_FORMAT.get().getFormat(), TimeContext.INGAME, DLTime.defaultTimeSystem());

        // Destination: from train or fallback to last stop
        String dest = d.getCurrentStop().map(s -> s.getDestination()).orElse("");
        if (dest.isBlank() && !stops.isEmpty())
            dest = stops.get(stops.size() - 1).getRealTimeStation().tagName();

        // Station name: next stop (matches BERJRESide pattern)
        String stnName = d.getNextStop().map(s -> s.getRealTimeStation().tagName())
            .orElse(stops.get(0).getRealTimeStation().tagName());

        UPPER.render(g, new UpperLCDRenderer.Model(
            dest,
            cat, routeColor,
            prefix(d),
            stnName,
            timeStr, routeColor));

        lower(g, stops, cfg.circularMode(), routeColor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Lower LCD (inline for now — to be extracted)
    // ═══════════════════════════════════════════════════════════════════════════

    private void lower(Graphics2D g, List<TrainStopDisplayData> stops, boolean circular, Color routeColor) {
        g.setClip(0, UH, SW, SH - UH);
        if (circular && stops.size() > 2) lowerCirc(g, stops, routeColor);
        else lowerRows(g, stops, routeColor);
        g.setClip(null);
    }

    // E235-1000 JapaneseDisplay — two-row bar layout (JapaneseDisplay.show_stops)
    private void lowerRows(Graphics2D g, List<TrainStopDisplayData> stops, Color rc) {
        int n = stops.size();
        if (n == 0) return;
        Font nf = font(FONT_STOPS), tf = font(FONT_TIME);

        // _calculate_layout
        int perLine = (n <= STOPS_PER_LINE) ? n : Math.min(STOPS_PER_LINE, (int)Math.ceil(n / 2.0));
        int stopsW = STOPS_WIDTH;                          // 42
        int effective = Math.min(perLine, n);
        int x = (SW - stopsW * effective) / 2;             // centered
        int y = (int)(SH * 0.28);                          // 117
        int barH = STOPS_BAR_HEIGHT;                       // 30
        int hLine = (n > perLine) ? 105 : 150;
        int topPad = 40;
        int destIdx = n - 1;
        int curr = 0;                                      // CRN: no passed data, list starts at current

        int rowHeadExtra = 10, rowTailExtra = 10;

        // Bars (show_stops)
        for (int i = 0; i < n; i++) {
            int localI = i;
            int ptr = (localI % perLine) * stopsW;
            int line = (localI < perLine) ? 1 : 2;
            int lY = y + hLine * line + topPad * (line - 1);

            boolean active = (i >= curr && i <= destIdx);
            Color cell = active ? rc : INACTIVE;

            boolean isR1Head = localI == 0;
            boolean isR2Head = localI == perLine;
            boolean isR1Tail = localI == perLine - 1;
            boolean isR2Tail = i == n - 1;
            int leftExtra = (isR1Head || isR2Head) ? rowHeadExtra : 0;
            int rightExtra = (isR1Tail || isR2Tail) ? rowTailExtra : 0;

            g.setColor(cell);
            g.fillRect(x + ptr - leftExtra, lY, stopsW + leftExtra + rightExtra, barH);
            // Row-end taper: triangle always at row end; chevrons only when 2 rows (route continues)
            if (isR1Tail) {
                int minuteW = Math.max(10, g.getFontMetrics(tf).stringWidth("分"));
                int tailX = x + ptr + stopsW + rightExtra;
                g.setColor(cell);
                g.fillRect(tailX, lY, minuteW, barH);
                int triX = tailX + minuteW;
                g.fillPolygon(new int[]{triX, triX, triX + 8}, new int[]{lY, lY + barH, lY + barH / 2}, 3);
                if (n > perLine) {
                    int contChevW = 12, contChevGap = -4;
                    int chevX = triX + 8 + contChevGap;
                    for (int c = 0; c < 2; c++) {
                        int cx = chevX + c * (contChevW + contChevGap);
                        int s = 4;
                        g.fillPolygon(new int[]{cx, cx + contChevW - s, cx + contChevW, cx + contChevW - s, cx, cx + s},
                            new int[]{lY, lY, lY + barH / 2, lY + barH, lY + barH, lY + barH / 2}, 6);
                    }
                }
            }
        }

        // Station names — vertical kanji above bar (draw_station_name at l_y-7)
        for (int i = 0; i < n; i++) {
            int localI = i;
            int ptr = (localI % perLine) * stopsW;
            int line = (localI < perLine) ? 1 : 2;
            int lY = y + hLine * line + topPad * (line - 1);
            boolean active = (i >= curr && i <= destIdx);
            g.setColor(active ? DARK_BG : INACTIVE);
            String nm = stops.get(i).getRealTimeStation().tagName();
            drawVert(g, nm, nf, x + ptr + stopsW / 2, lY - 7 - nf.getSize() * nm.length());
        }

        // Marks (draw_marks) — white ring r=11 active, small dot r=5 inactive,
        // inner red disk at curr_stop
        for (int i = 0; i < n; i++) {
            int localI = i;
            int ptr = (localI % perLine) * stopsW;
            int line = (localI < perLine) ? 1 : 2;
            int lY = y + hLine * line + topPad * (line - 1);
            int cx = x + ptr + stopsW / 2;
            int cy = lY + barH / 2;
            if (i >= curr && i <= destIdx) {
                g.setColor(WHITE_BG); g.fillOval(cx - 11, cy - 11, 22, 22);
                g.setColor(DARK_BG); g.drawOval(cx - 11, cy - 11, 22, 22);
                if (i == curr) { g.setColor(CURRENT_COLOR); g.fillOval(cx - 9, cy - 9, 18, 18); }
            } else {
                g.setColor(WHITE_BG); g.fillOval(cx - 5, cy - 5, 10, 10);
                g.setColor(DARK_BG); g.drawOval(cx - 5, cy - 5, 10, 10);
            }
        }

        // Pointer pentagon at curr (draw_ptr — right-pointing pentagon + halo)
        drawPtrPentagon(g, x + (curr % perLine) * stopsW, y + hLine * 1, barH);

        // Travel times inside bars (draw_times)
        for (int i = 1; i < n; i++) {
            int localI = i;
            int ptr = (localI % perLine) * stopsW;
            int line = (localI < perLine) ? 1 : 2;
            int lY = y + hLine * line + topPad * (line - 1);
            eta(g, tf, stops.get(i), x + ptr + stopsW / 2, lY + barH / 2 + 5);
        }

        // Disclaimer bottom-right (draw_route_disclaimer)
        g.setFont(font(10)); g.setColor(DARK_BG);
        String disc = "のりかえ、待合せ時間は含まれません。電車により多少時間が異なります。一部区間では時間を表示しません。";
        int dw = g.getFontMetrics().stringWidth(disc);
        g.drawString(disc, SW - 8 - dw, SH - 4);
    }

    // E235-1000 draw_ptr STOPPING pentagon — body + right apex + halo
    private void drawPtrPentagon(Graphics2D g, int cellLeft, int barY, int barH) {
        int stopsW = STOPS_WIDTH;
        int overhang = 1, shiftX = -3, triDepth = 10, dotR = 6, halo = 3;
        int leftX = cellLeft + shiftX;
        int rectRight = cellLeft + stopsW + shiftX;
        int apexX = rectRight + triDepth;
        int[] xs = {leftX, leftX, rectRight, apexX, rectRight};
        int[] ys = {barY - overhang, barY + barH + overhang, barY + barH + overhang,
                    barY + barH / 2, barY - overhang};
        // Halo (two x-shifted copies)
        g.setColor(WHITE_BG);
        g.fillPolygon(shift(xs, -halo), ys, 5);
        g.fillPolygon(shift(xs, +halo), ys, 5);
        // Body
        g.setColor(RED);
        g.fillPolygon(xs, ys, 5);
        // Interior light dot
        g.setColor(WHITE_BG);
        g.fillOval(cellLeft + stopsW / 2 - dotR, barY + barH / 2 - dotR, dotR * 2, dotR * 2);
    }

    private static int[] shift(int[] arr, int d) {
        int[] r = new int[arr.length];
        for (int i = 0; i < arr.length; i++) r[i] = arr[i] + d;
        return r;
    }

    // OpenRouteFullRouteDisplay — horseshoe: right fold cap kept, left side
    // clipped flat per OpenRouteFullRouteDisplay._draw_track()/_build_positions()

    private void lowerCirc(Graphics2D g, List<TrainStopDisplayData> stops, Color rc) {
        int n = stops.size(), half = (n + 1) / 2;
        Font nf = font(FONT_STOPS), tf = font(FONT_TIME);
        int cy = UH + 153, vO = 47, vI = 19, bO = 37, bI = 12, l = 15, r = 15, sw = 28;
        g.setColor(rc);
        g.fillRoundRect(l, cy - vO, SW - l - r, 2*vO, bO, bO);
        g.setColor(WHITE_BG);
        g.fillRoundRect(l + sw, cy - vI, SW - l - r - 2*sw, 2*vI, bI, bI);
        int tT = UH + 120, tB = UH + 187, ew = SW - l - r;
        float spB = half > 1 ? (float)ew / (half - 1) : 0;
        for (int i = 0; i < half; i++) {
            int cx = l + (int)(spB * i); boolean cur = (i == 0);
            marker(g, cx, tB, cur);
            var s = stops.get(i);
            g.setFont(nf); g.setColor(cur ? DARK_BG : DARK_BG);
            String nm = s.getRealTimeStation().tagName();
            drawVert(g, nm, nf, cx, tB+4);
            if (!cur) eta(g, tf, s, cx, tB - 8);
        }
        int tn = n - half;
        if (tn > 0) {
            float spT = tn > 1 ? (float)ew / (tn - 1) : 0;
            for (int i = 0; i < tn; i++) {
                int cx = l + (int)(spT * (tn - 1 - i));
                var s = stops.get(half + i);
                g.setColor(WHITE_BG); g.fillOval(cx - 5, tT - 5, 10, 10);
                g.setColor(DARK_BG); g.drawOval(cx - 5, tT - 5, 10, 10);
                g.setFont(nf); g.setColor(DARK_BG);
                String nm = s.getRealTimeStation().tagName();
                drawVert(g, nm, nf, cx, tT - nf.getSize() * nm.length());
                eta(g, tf, s, cx, tT + 18);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Primitives
    // ═══════════════════════════════════════════════════════════════════════════

    private void marker(Graphics2D g, int cx, int cy, boolean cur) {
        if (cur) {
            int r = 24;
            g.setColor(RED);
            // Apex UP pentagon (CCW 90° from original)
            g.fillPolygon(new int[]{cx-r, cx-r, cx+r/2, cx+r, cx+r/2},
                new int[]{cy+r, cy-r, cy-r, cy, cy+r}, 5);
            g.setColor(WHITE_BG); g.fillOval(cx-12, cy-12, 24, 24);
        } else {
            g.setColor(WHITE_BG); g.fillOval(cx-16, cy-16, 32, 32);
            g.setColor(DARK_BG); g.drawOval(cx-16, cy-16, 32, 32);
        }
    }

    private void eta(Graphics2D g, Font tf, TrainStopDisplayData s, int cx, int top) {
        long t = s.getScheduledArrivalTime() - ModUtils.getTransformedWorldTime();
        if (t <= 0) return;
        String v = String.valueOf(Math.max(0, t / 20 / 60));
        g.setFont(tf); g.setColor(DARK_BG);
        g.drawString(v, cx - g.getFontMetrics().stringWidth(v)/2, top);
    }

    private void oos(Graphics2D g, boolean nb) {
        g.setColor(WHITE_BG); g.setFont(font(40));
        String t = nb ? "DO NOT BOARD" : "OUT OF SERVICE";
        g.drawString(t, (SW - g.getFontMetrics().stringWidth(t))/2, SH/2);
    }

    private static String prefix(TrainDisplayData d) {
        if (d.isWaitingAtStation()) return "ただいま";
        if (d.getNextStop().isPresent()) {
            long tta = d.getNextStop().get().getRealTimeArrivalTime() - ModUtils.getTransformedWorldTime();
            if (tta > 0 && tta < 100) return "まもなく";
        }
        return "次は";
    }

    private static Font font(int size) {
        return FontLoader.bold(size);
    }

    private void drawVert(Graphics2D g, String text, Font font, int cx, int top) {
        g.setFont(font);
        text = com.hybrizat.crndisplaynext.client.KanjiConverter.toDisplay(text, font);
        int asc = g.getFontMetrics().getAscent();
        for (int j = 0; j < text.length(); j++) {
            String ch = text.substring(j, j + 1);
            int cw = g.getFontMetrics().stringWidth(ch);
            g.drawString(ch, cx - cw/2, top + asc + j * font.getSize());
        }
    }
    public static void release(long key) { var h = H.remove(key); if (h != null) h.close(); }
}
