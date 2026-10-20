package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.client.DynamicTextureHolder;
import com.hybrizat.crndisplaynext.client.TextureHolderSweeper;
import com.hybrizat.crndisplaynext.client.FontLoader;
import com.hybrizat.crndisplaynext.display.renderer.UpperLCDRenderer;
import com.hybrizat.crndisplaynext.display.renderer.LayoutConfig;
import com.hybrizat.crndisplaynext.display.settings.JREVISSettings;
import com.hybrizat.crndisplaynext.train.TrainView;
import de.mrjulsen.crn.CreateRailwaysNavigator;
import de.mrjulsen.crn.api.core.StopVisitState;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.crn.client.lang.CustomLanguage;
import de.mrjulsen.crn.config.ModClientConfig;
import de.mrjulsen.crn.util.ModUtils;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.util.RenderUtils;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.time.DLTime;
import de.mrjulsen.mcdragonlib.util.time.TimeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.IdentityHashMap;

/**
 * JRE Passenger VIS — E235-0 replica.
 * Coordinates the canvas (730×420 → DynamicTexture) and delegates
 * drawing to UpperLCDRenderer / LowerLCD (inline for now).
 */
public class BERJREPassengerVIS implements AbstractAdvancedDisplayRenderer<JREVISSettings> {

    private static final String KEY_OOS      = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.not_in_service";
    private static final String KEY_NO_BOARD = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.do_not_board";

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
    private static final Color PASSED_COLOR = new Color(80, 80, 80); // Gray for passed stations

    private static final int FONT_STOPS = 22, FONT_TIME = 14;

    enum Page { FULL_ROUTE, FIVE_STATION }
    /**
     * A holder plus the last client tick in which its display was confirmed alive
     * (CRN called tick/render/update for this exact block entity instance).
     */
    private static final class Entry {
        AdvancedDisplayBlockEntity be; // re-bindable: same display, new BE after chunk reload
        final DynamicTextureHolder holder;
        int lastActive;
        /** Signature of the data the canvas was last painted from. */
        String sig = "";

        Entry(AdvancedDisplayBlockEntity be, DynamicTextureHolder holder) {
            this.be = be;
            this.holder = holder;
            this.lastActive = TextureHolderSweeper.clientTicks;
        }

        void touch() { lastActive = TextureHolderSweeper.clientTicks; }
    }
    /** A display is considered dead after this many ticks without renderer activity. */
    static final int STALE_AFTER_TICKS = 200;
    private static final Map<AdvancedDisplayBlockEntity, Entry> H = new IdentityHashMap<>();
    private Page page = Page.FULL_ROUTE;
    private long pageStart;
    private int cw = 16, ch = 16;
    /** Ticks between data-signature checks that can trigger a repaint. */
    private int resyncCounter;
    private static final int RESYNC_INTERVAL_TICKS = 5;

    @Override public void tick(Level lv, BlockPos pos, BlockState st,
                                AdvancedDisplayBlockEntity be, AdvancedDisplayRenderInstance p) {
        if (!be.isController()) return;
        Entry e = H.get(be);
        if (e != null && e.be == be) e.touch();
        long t = ModUtils.getTransformedWorldTime();
        if (t - pageStart >= getDisplaySettings(be).getPageIntervalSecs() * 20L) {
            page = (page == Page.FULL_ROUTE) ? Page.FIVE_STATION : Page.FULL_ROUTE;
            pageStart = t;
        }
        // Self-heal: repaint whenever the data behind the canvas no longer matches
        // what it was painted from. The canvas is cached by position and deliberately
        // survives block-entity recreation, so it can otherwise keep an arrangement
        // from before a reload (e.g. a two-row route where the remaining stops now fit
        // one row) until some unrelated CRN update happens to fire. Repainting on a
        // data change makes the display converge no matter how it was reloaded.
        if (++resyncCounter >= RESYNC_INTERVAL_TICKS) {
            resyncCounter = 0;
            Entry cur = H.get(be);
            if (cur != null) {
                cur.touch();
                String sig = signature(be);
                if (!sig.equals(cur.sig)) {
                    cur.sig = sig;
                    cur.be = be;
                    repaint(cur, be, lv);
                }
            }
        }
    }

    /** Cheap fingerprint of everything the canvas is drawn from. */
    private static String signature(AdvancedDisplayBlockEntity be) {
        TrainView d = TrainView.of(be);
        if (d == null) return "-";
        List<TrainView.StopView> stops = d.route();
        StringBuilder sb = new StringBuilder();
        sb.append(d.waiting() ? 'W' : 'R').append(stops.size()).append('|').append(d.destination()).append('|');
        for (TrainView.StopView s : stops) {
            sb.append(s.name()).append('/');
        }
        return sb.toString();
    }

    /**
     * Sizes the quad from the size the block entity itself declares.
     *
     * <p>Probing neighbouring blocks does NOT work for a materialised display: its
     * block entities sit at contraption-local coordinates while {@code getLevel()}
     * returns the plain {@link ClientLevel}, so every neighbour lookup returned
     * {@code null} and a measured extent collapsed to 1x1 (rendering a 16x16 quad
     * instead of 48x32). CRN exposes no contraption-aware size query, so the declared
     * size is the only trustworthy source.</p>
     */
    private void applyOwnedExtent(AdvancedDisplayBlockEntity be, Level lv) {
        cw = be.getXSizeScaled() * 16;
        ch = be.getYSizeScaled() * 16;
    }

    @Override public void render(BERGraphics<AdvancedDisplayBlockEntity> g, float pt,
                                  AdvancedDisplayRenderInstance p, int l, boolean bs) {
        var be = g.blockEntity();
        if (!be.isController()) return;
        applyOwnedExtent(be, be.getLevel());
        Entry entry = H.get(be);
        if (entry != null) {
            applyOwnedExtent(be, be.getLevel());
        }
        if (entry == null || !entry.holder.isReady()) return;
        entry.touch();
        RenderUtils.renderTexture(entry.holder.getId(), g,
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
        applyOwnedExtent(be, lv);
        Entry entry = H.get(be);
        if (entry == null) {
            entry = new Entry(be, new DynamicTextureHolder(SW, SH));
            H.put(be, entry);
        }
        entry.touch();
        entry.sig = signature(be);
        repaint(entry, be, lv);
    }

    /**
     * Rebuilds the whole canvas from live data.
     *
     * <p>Shared by the normal update path and by the chunk-reload rebind: the canvas
     * is keyed by block position and deliberately survives a block-entity
     * recreation, but it then still holds the layout from before the reload. Without
     * repainting here the display kept showing the old route arrangement — e.g. a
     * two-row layout when the remaining stops now fit on one row — until something
     * else triggered a CRN update (opening the settings and confirming).</p>
     */
    private void repaint(Entry entry, AdvancedDisplayBlockEntity be, Level lv) {
        var cfg = getDisplaySettings(be);
        applyOwnedExtent(be, lv);
        var holder = entry.holder;
        holder.resize(SW, SH);
        BufferedImage img = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        try { draw(g2, be, cfg, lv); } catch (Exception e) { CRNDisplayNextMod.LOGGER.error("[VIS]", e); }
        g2.dispose();
        holder.copyFromBuffered(img);
        holder.upload();
        holder.markReady();
        // Diagnostic: the display quad is sized from cw/ch while the canvas is always
        // SW x SH. If the two aspects disagree badly the content is squashed or
        // repeated, so record both whenever a repaint actually happens.
        CRNDisplayNextMod.LOGGER.debug("[VIS] repaint at {} canvas={}x{} quad={}x{} size={}x{} type={}",
            be.getBlockPos(), SW, SH, cw - 4, ch - 4,
            be.getXSizeScaled(), be.getYSizeScaled(), be.getDisplayType());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Master draw — delegates upper to UpperLCDRenderer, keeps lower inline
    // ═══════════════════════════════════════════════════════════════════════════

    private void draw(Graphics2D g, AdvancedDisplayBlockEntity be, JREVISSettings cfg, Level lv) {
        g.setColor(DARK_BG); g.fillRect(0, 0, SW, SH);
        g.setColor(WHITE_BG); g.fillRect(0, UH, SW, SH - UH);
        TrainView d = TrainView.of(be);
        if (d == null || d.isIrregular(cfg.showDoNotBoardText())) {
            // Which of the two texts applies depends on the same setting that got us
            // here: with the soft terminus included, SOFT_TERMINUS_ANNOUNCED must read
            // as "do not board" too, not as a generic out-of-service.
            oos(g, d != null && d.shouldNotBoard(cfg.showDoNotBoardText()));
            return;
        }
        List<TrainView.StopView> stops = d.route();
        if (stops.isEmpty()) return;

        // Build model from CRN data. The badge is the 種別 where one is configured and
        // the line name otherwise; the ribbon colour follows whichever was chosen, and
        // each stop below may override it with its own section's colour.
        Color routeColor = toAwt(d.badgeColor(), GREEN);
        String cat = d.badge();
        String timeStr = new DLTime(lv, DLTime.defaultTimeSystem())
            .format(ModClientConfig.TIME_FORMAT.get().getFormat(), TimeContext.INGAME, DLTime.defaultTimeSystem());

        // 行先, already resolved through the through-service chain by TrainView.
        String dest = d.destination();
        if (dest.isBlank() && !stops.isEmpty())
            dest = stops.get(stops.size() - 1).name();

        // Station name must agree with prefix(): "ただいま" names the station the train is
        // standing at, "次は" names the one it is heading for. Always using the next
        // stop made a dwelling train read "ただいま <次站>".
        TrainView.StopView named = d.waiting() ? d.currentStop() : null;
        if (named == null) named = d.nextStop();
        String stnName = named != null ? named.name() : stops.get(0).name();

        UPPER.render(g, new UpperLCDRenderer.Model(
            dest,
            cat, routeColor,
            prefix(d),
            stnName,
            timeStr, routeColor));

        lower(g, stops, cfg.circularMode(), routeColor, d.waiting());
    }

    /** CRN colour to AWT, falling back when the line/category has no colour of its own. */
    private static Color toAwt(DLColor color, Color fallback) {
        return color == null || color.isTransparent() ? fallback : new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Lower LCD (inline for now — to be extracted)
    // ═══════════════════════════════════════════════════════════════════════════

    private void lower(Graphics2D g, List<TrainView.StopView> stops, boolean circular, Color routeColor, boolean dwelling) {
        g.setClip(0, UH, SW, SH - UH);
        if (circular && stops.size() > 2) lowerCirc(g, stops, routeColor, dwelling);
        else lowerRows(g, stops, routeColor, dwelling);
        g.setClip(null);
    }

    // E235-1000 JapaneseDisplay — two-row bar layout (JapaneseDisplay.show_stops)
    private void lowerRows(Graphics2D g, List<TrainView.StopView> stops, Color rc, boolean dwelling) {
        int n = stops.size();
        if (n == 0) return;
        Font nf = font(FONT_STOPS), tf = font(FONT_TIME);

        // Where the train is in the list. Passed stops sit in front of it, so the
        // pointer belongs on the first stop that is not passed — NOT on index 0, which
        // is a station the train has already left whenever it is between stations.
        int curr = 0;
        for (int i = 0; i < n; i++) {
            StopVisitState vs = stops.get(i).visitState();
            if (vs == StopVisitState.CURRENT || vs == StopVisitState.UPCOMING) {
                curr = i;
                break;
            }
        }

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

        int rowHeadExtra = 10, rowTailExtra = 10;

        // Bars (show_stops) - passed stations are gray, current/upcoming use route color
        for (int i = 0; i < n; i++) {
            int localI = i;
            int ptr = (localI % perLine) * stopsW;
            int line = (localI < perLine) ? 1 : 2;
            int lY = y + hLine * line + topPad * (line - 1);

            StopVisitState visitState = stops.get(i).visitState();
            boolean isPassed = visitState == StopVisitState.PASSED;
            boolean active = !isPassed;
            // Per-section colour when the stop carries one (through-service journeys
            // span several lines), otherwise the display-wide route colour.
            Color own = toAwt(stops.get(i).routeColor(), rc);
            Color cell = isPassed ? PASSED_COLOR : (active ? own : INACTIVE);

            boolean isR1Head = localI == 0;
            boolean isR2Head = localI == perLine;
            boolean isR1Tail = localI == perLine - 1;
            boolean isR2Tail = i == n - 1;
            int leftExtra = (isR1Head || isR2Head) ? rowHeadExtra : 0;
            int rightExtra = (isR1Tail || isR2Tail) ? rowTailExtra : 0;

            g.setColor(cell);
            // The head/tail overhang must not shift the CELL CENTRE: the station dot
            // and its name are both drawn at x + ptr + stopsW/2, and growing the bar
            // to the left at the head (or right at the tail) moved the bar's centre
            // 5px away from them.
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
            
            de.mrjulsen.crn.api.core.StopVisitState visitState = stops.get(i).visitState();
            boolean isPassed = visitState == StopVisitState.PASSED;
            boolean active = !isPassed;
            g.setColor(isPassed ? PASSED_COLOR : (active ? DARK_BG : INACTIVE));
            drawVert(g, stops.get(i).name(), nf, x + ptr + stopsW / 2, lY - 7 - nf.getSize() * stops.get(i).name().length());
        }

        // Marks (draw_marks) — white ring r=11 active, small dot r=5 inactive,
        // inner red disk at curr_stop, gray for passed
        for (int i = 0; i < n; i++) {
            int localI = i;
            int ptr = (localI % perLine) * stopsW;
            int line = (localI < perLine) ? 1 : 2;
            int lY = y + hLine * line + topPad * (line - 1);
            int cx = x + ptr + stopsW / 2;
            int cy = lY + barH / 2;
            
            de.mrjulsen.crn.api.core.StopVisitState visitState = stops.get(i).visitState();
            boolean isPassed = visitState == StopVisitState.PASSED;
            boolean active = !isPassed;

            if (active) {
                g.setColor(WHITE_BG); g.fillOval(cx - 11, cy - 11, 22, 22);
                g.setColor(DARK_BG); g.drawOval(cx - 11, cy - 11, 22, 22);
                // JR East highlights the station the train is bound for — the one it
                // stands at while dwelling, the one it is approaching while running.
                if (i == curr) { g.setColor(CURRENT_COLOR); g.fillOval(cx - 9, cy - 9, 18, 18); }
            } else {
                // Passed stations: small gray dot
                g.setColor(PASSED_COLOR); g.fillOval(cx - 5, cy - 5, 10, 10);
                g.setColor(DARK_BG); g.drawOval(cx - 5, cy - 5, 10, 10);
            }
        }

        // Pointer pentagon (draw_ptr) marks the station the train is STOPPING at.
        // It is only meaningful while dwelling, so it is suppressed while running
        // rather than being dragged onto the station ahead.
        //
        // The y position must follow the row the marked station is actually drawn in:
        // a long route wraps onto a second row, whose bars sit at
        // y + hLine*2 + topPad. Hard-coding the first row's y left the marker floating
        // over the wrong bar as soon as the train moved into the wrapped part.
        int ptrLine = (curr < perLine) ? 1 : 2;
        int ptrY = y + hLine * ptrLine + topPad * (ptrLine - 1);
        if (dwelling) {
            drawPtrPentagon(g, x + (curr % perLine) * stopsW, ptrY, barH);
        } else {
            // Running: a green/dark arrow on the boundary, pointing at the station the
            // train is bound for (JR East). Its colour follows that station's ribbon.
            Color bound = toAwt(stops.get(curr).routeColor(), rc);
            drawTravelArrow(g, Math.max(x, x + (curr % perLine) * stopsW - TRAVEL_ARROW_W), ptrY, barH, bound);
        }

        // Travel times inside bars (draw_times) - only for upcoming stations
        for (int i = 0; i < n; i++) {
            StopVisitState visitState = stops.get(i).visitState();
            if (visitState == StopVisitState.UPCOMING) {
                int localI = i;
                int ptr = (localI % perLine) * stopsW;
                int line = (localI < perLine) ? 1 : 2;
                int lY = y + hLine * line + topPad * (line - 1);
                eta(g, tf, stops.get(i), x + ptr + stopsW / 2, lY + barH / 2 + 5);
            }
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

    private void lowerCirc(Graphics2D g, List<TrainView.StopView> stops, Color rc, boolean dwelling) {
        int n = stops.size(), half = (n + 1) / 2;
        Font nf = font(FONT_STOPS), tf = font(FONT_TIME);
        int cy = UH + 153, vO = 47, vI = 19, bO = 37, bI = 12, l = 15, r = 15, sw = 28;
        g.setColor(rc);
        g.fillRoundRect(l, cy - vO, SW - l - r, 2*vO, bO, bO);
        g.setColor(WHITE_BG);
        g.fillRoundRect(l + sw, cy - vI, SW - l - r - 2*sw, 2*vI, bI, bI);
        int tT = UH + 120, tB = UH + 187, ew = SW - l - r;
        float spB = half > 1 ? (float)ew / (half - 1) : 0;
        // The pointer marks the station the train is at or heading to, which is the
        // first stop that is not passed (passed stops are rendered in grey in front of it).
        int curIdx = 0;
        for (int i = 0; i < n; i++) {
            StopVisitState vs = stops.get(i).visitState();
            if (vs == StopVisitState.CURRENT || vs == StopVisitState.UPCOMING) {
                curIdx = i;
                break;
            }
        }
        for (int i = 0; i < half; i++) {
            // The red pentagon only marks a station the train is stopping at, so it is
            // suppressed while running (the plain circle is drawn instead).
            boolean cur = dwelling && (i == curIdx);
            int cx = l + (int)(spB * i);
            marker(g, cx, tB, cur);
            var s = stops.get(i);
            boolean passed = s.isPassed();
            g.setFont(nf); g.setColor(passed ? PASSED_COLOR : DARK_BG);
            String nm = s.name();
            drawVert(g, nm, nf, cx, tB+4);
            if (!cur && !passed) eta(g, tf, s, cx, tB - 8);
        }
        int tn = n - half;
        if (tn > 0) {
            float spT = tn > 1 ? (float)ew / (tn - 1) : 0;
            for (int i = 0; i < tn; i++) {
                int cx = l + (int)(spT * (tn - 1 - i));
                var s = stops.get(half + i);
                // The pointer belongs on whichever row the marked station is drawn in.
                // Only the lower row used to get one, so a train dwelling in the wrapped
                // half of the horseshoe had no marker at all.
                if (dwelling && half + i == curIdx) {
                    marker(g, cx, tT, true);
                } else {
                    g.setColor(WHITE_BG); g.fillOval(cx - 5, tT - 5, 10, 10);
                    g.setColor(DARK_BG); g.drawOval(cx - 5, tT - 5, 10, 10);
                }
                g.setFont(nf); g.setColor(DARK_BG);
                String nm = s.name();
                drawVert(g, nm, nf, cx, tT - nf.getSize() * nm.length());
                eta(g, tf, s, cx, tT + 18);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Primitives
    // ═══════════════════════════════════════════════════════════════════════════

    private static final int TRAVEL_ARROW_W = 18;
    /** Two arrow colours, picked against the ribbon so the arrow is never invisible. */
    private static final Color ARROW_DARK_RED = new Color(139, 24, 24);
    private static final Color ARROW_DARK_GREEN = new Color(27, 106, 20);

    /**
     * The travel arrow must stay visible against the route ribbon, whose colour is the
     * line/category colour and therefore arbitrary. Two fixed variants are swapped by
     * hue: a green ribbon gets the dark red arrow and anything else the dark green one.
     */
    private static Color arrowColorFor(Color ribbon) {
        boolean greenish = ribbon.getGreen() >= ribbon.getRed() && ribbon.getGreen() >= ribbon.getBlue();
        return greenish ? ARROW_DARK_RED : ARROW_DARK_GREEN;
    }

    /**
     * Green/dark right-pointing arrow drawn on the boundary between the station the
     * train left and the station it is approaching, per the JR East layout. Only shown
     * while running; the red pentagon is used while dwelling instead.
     */
    private void drawTravelArrow(Graphics2D g, int leftX, int barY, int barH, Color ribbon) {
        int midY = barY + barH / 2;
        int notch = TRAVEL_ARROW_W - 9;
        g.setColor(arrowColorFor(ribbon));
        g.fillPolygon(
            new int[]{leftX, leftX + notch, leftX + TRAVEL_ARROW_W, leftX + notch, leftX},
            new int[]{barY, barY, midY, barY + barH, barY + barH}, 5);
    }

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

    private void eta(Graphics2D g, Font tf, TrainView.StopView s, int cx, int top) {
        long t = s.scheduledArrival() - ModUtils.getTransformedWorldTime();
        if (t <= 0) return;
        String v = String.valueOf(Math.max(0, t / 20 / 60));
        g.setFont(tf); g.setColor(DARK_BG);
        g.drawString(v, cx - g.getFontMetrics().stringWidth(v)/2, top);
    }

    private void oos(Graphics2D g, boolean nb) {
        g.setColor(WHITE_BG); g.setFont(font(40));
        // CRN's own keys, so the text follows the game language instead of being
        // hardcoded English like the rest of the VIS chrome.
        String key = nb ? KEY_NO_BOARD : KEY_OOS;
        String t = CustomLanguage.translate(key).getString();
        g.drawString(t, (SW - g.getFontMetrics().stringWidth(t))/2, SH/2);
    }

    private static String prefix(TrainView d) {
        if (d.waiting()) return "ただいま";
        if (d.nextStop() != null) {
            long tta = d.nextStop().realtimeArrival() - ModUtils.getTransformedWorldTime();
            if (tta > 0 && tta < 100) return "まもなく";
        }
        return "次は";
    }

    private static Font font(int size) {
        return FontLoader.bold(size);
    }

    private void drawVert(Graphics2D g, String text, Font font, int cx, int top) {
        text = com.hybrizat.crndisplaynext.client.KanjiConverter.toDisplay(text, font);
        for (int j = 0; j < text.length(); j++) {
            char c = text.charAt(j);
            Font f = FontLoader.resolveFor(font, c);
            String ch = String.valueOf(c);
            // g.setFont(f) is REQUIRED: g.drawString renders with the font currently
            // set on the Graphics2D, not with the one passed to getFontMetrics(). The
            // CJK fallback face has different metrics from the primary face, so
            // measuring one and drawing the other shifted each name sideways.
            g.setFont(f);
            var fm = g.getFontMetrics();
            int cw = fm.stringWidth(ch);
            g.drawString(ch, cx - cw / 2, top + fm.getAscent() + j * f.getSize());
        }
    }
    /**
     * Activity-based reclamation: release a holder only after its display has
     * been quiet for STALE_AFTER_TICKS (block unloaded, removed, or the
     * carriage dismantled). Live displays - including carriage-mounted VIS,
     * whose block entity never lives in the level's BE map - keep getting
     * touched by CRN's renderer calls, so they are never reaped.
     */
    public static void cleanup() {
        int now = TextureHolderSweeper.clientTicks;
        for (AdvancedDisplayBlockEntity key : new ArrayList<>(H.keySet())) {
            Entry e = H.get(key);
            if (e != null && now - e.lastActive >= STALE_AFTER_TICKS) release(key);
        }
    }

    public static void release(AdvancedDisplayBlockEntity key) { var e = H.remove(key); if (e != null) e.holder.close(); }
}
