package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import com.hybrizat.crndisplaynext.display.EJRETrainTextMode;
import com.hybrizat.crndisplaynext.display.settings.AbstractJRESettings;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.block.properties.ETimeDisplay;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.crn.client.lang.CustomLanguage;
import de.mrjulsen.crn.config.ModCommonConfig;
import de.mrjulsen.crn.data.train.ETrainStopState;
import de.mrjulsen.crn.data.train.portable.BasicTrainDisplayData;
import de.mrjulsen.crn.data.train.portable.StationDisplayData;
import de.mrjulsen.crn.data.train.portable.TrainStopDisplayData;
import de.mrjulsen.crn.util.ModUtils;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel.EScrollMode;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.PaddingF;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.Pair;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Point;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared renderer for all JRE LED display variants.
 *
 * Layout (JR East style):  [Arrow?] [TrainText] [Time] [Destination方面] [Platform番線?] [Arrow?]
 *
 * Column width policy:
 *   FIXED (>= 0): use configured px value; text clips if too wide
 *   AUTO  (= -1): compute max rendered width across ALL rows once per update,
 *                 use that as the column width for ALL rows uniformly.
 *                 This ensures time column aligns across all rows.
 *
 * The two-pass layout:
 *   Pass 1: set text content on all row labels, measure rendered width per label
 *   Pass 2: compute per-column max width, then position all labels
 */
public abstract class AbstractJRERenderer<S extends AbstractJRESettings>
    implements AbstractAdvancedDisplayRenderer<S> {

    protected static final float LINE_HEIGHT     = 5.4f;
    protected static final float BASE_SCALE      = 0.4f;
    protected static final float BASE_H_SCALE_MIN = 0.2f;

    protected static final int COL_TRAIN_TEXT  = 0;
    protected static final int COL_TIME        = 1;
    protected static final int COL_DEST        = 2;
    protected static final int COL_PLATFORM    = 3;
    protected static final int COL_COUNT       = 4;

    protected List<StationDisplayData> lastDepartures = new ArrayList<>();
    public List<StationDisplayData> getLastDepartures() { return lastDepartures; }

    protected BERLabel[][] rows     = new BERLabel[0][];
    // Out-of-service label shown when no trains available
    private   BERLabel     oosLabel = new BERLabel();

    protected abstract boolean showPlatform();
    // Subclass returns reserved arrow width (left + right) so layout can offset
    protected float arrowReservedLeft(S settings)  { return 0f; }
    protected float arrowReservedRight(S settings) { return 0f; }

    // ── tick ──────────────────────────────────────────────────────────────
    @Override
    public void tick(Level level, BlockPos pos, BlockState state,
                     AdvancedDisplayBlockEntity be, AdvancedDisplayRenderInstance parent) {
        Rectangle clip = Rectangle.withSize(2, 2,
            be.getXSizeScaled() * 16 - 4, be.getYSizeScaled() * 16 - 4);
        boolean glow = be.isGlowing();
        oosLabel.clippingArea.set(clip);
        oosLabel.glowing.set(glow);
        for (BERLabel[] row : rows)
            for (BERLabel lbl : row) { lbl.clippingArea.set(clip); lbl.glowing.set(glow); }
    }

    // ── render ────────────────────────────────────────────────────────────
    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics,
                       float partial, AdvancedDisplayRenderInstance parent,
                       int light, boolean backSide) {
        if (rows.length == 0) {
            oosLabel.render(graphics, light);
            return;
        }
        for (BERLabel[] row : rows)
            for (BERLabel lbl : row)
                lbl.render(graphics, light);
    }

    // ── update ────────────────────────────────────────────────────────────
    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity be,
                       AdvancedDisplayRenderInstance parent, EUpdateReason reason) {

        if (!be.isController()) { rows = new BERLabel[0][]; return; }

        S settings = getDisplaySettings(be);
        int maxRows = be.getYSizeScaled() * 3 - 1;
        DLColor font = settings.getFontColor();

        // Update out-of-service label — full width, scrolls if needed
        int totalWForOos = be.getXSizeScaled() * 16;
        oosLabel.text.set(TextUtils.text(loc("gui.crndisplaynext.jre.out_of_service", "準備中")));
        oosLabel.color.set(font);
        oosLabel.position.set(Point.of(3, 3));
        oosLabel.verticalScale.set(Pair.of(BASE_SCALE, BASE_SCALE));
        oosLabel.horizontalScale.set(Pair.of(BASE_H_SCALE_MIN, BASE_SCALE));
        oosLabel.preferredWidth.set((float)(totalWForOos - 6));
        oosLabel.horizontalScrollMode.set(BERLabel.EScrollMode.WHEN_NEEDED);

        // Filter departures — mirrors BERPlatformSimple logic:
        //   getRealTimeArrivalTime < now + LEAD_TIME  → train is upcoming or current
        //   cancelled trains shown only until scheduledDeparture + LEAD_TIME
        long now = ModUtils.getTransformedWorldTime();
        lastDepartures = be.getStops().stream()
            .filter(x -> !x.isNextSectionExcluded())
            .filter(x -> x.getStationData().getRealTimeArrivalTime()
                         < now + ModCommonConfig.DISPLAY_LEAD_TIME.get())
            .filter(x -> !x.getTrainData().isCancelled()
                || now < x.getStationData().getScheduledDepartureTime()
                         + ModCommonConfig.DISPLAY_LEAD_TIME.get())
            .limit(maxRows)
            .collect(Collectors.toList());

        int count = lastDepartures.size();
        if (count == 0) { rows = new BERLabel[0][]; return; }

        // Rebuild label grid if needed
        if (reason == EUpdateReason.LAYOUT_CHANGED || rows == null || rows.length != count) {
            rows = new BERLabel[count][];
            for (int i = 0; i < count; i++) rows[i] = createRow(be, settings);
        }

        // ── Two-pass layout ───────────────────────────────────────────────
        // Pass 1: populate text content in all labels so getRenderedWidth() is valid
        for (int i = 0; i < count; i++) {
            populateText(rows[i], lastDepartures.get(i), settings, font);
        }

        // Pass 2: compute column widths (max across all rows for AUTO columns)
        float ttW   = computeColWidth(settings.getTrainTextWidth(), settings.isAutoTrainTextWidth(),
                                      COL_TRAIN_TEXT,  count);
        float timeW = computeColWidth(settings.getTimeWidth(),      settings.isAutoTimeWidth(),
                                      COL_TIME,        count);

        // Pass 3: position all labels using uniform column widths
        int totalW = be.getXSizeScaled() * 16;
        float leftOff  = arrowReservedLeft(settings);
        float rightOff = arrowReservedRight(settings);

        // Platform width (right-anchored): compute max across rows if shown
        float platW = 0;
        if (showPlatform()) {
            for (int i = 0; i < count; i++) {
                float w = rows[i][COL_PLATFORM].getRenderedWidth();
                if (w > platW) platW = w;
            }
            if (platW > 0) platW += 2; // padding
        }

        float destX    = leftOff + 3 + ttW + timeW;
        float destMaxW = totalW - 3 - destX - (platW > 0 ? platW + 3 : 0) - rightOff;

        for (int i = 0; i < count; i++) {
            positionRow(rows[i], i, leftOff, ttW, timeW, destMaxW,
                        totalW, platW, settings.getDestWidth(), settings.isAutoDestWidth(), font);
        }
    }

    // ── Row factory ───────────────────────────────────────────────────────
    private BERLabel[] createRow(AdvancedDisplayBlockEntity be, S settings) {
        BERLabel[] row = new BERLabel[COL_COUNT];
        Rectangle clip = Rectangle.withSize(2, 2,
            be.getXSizeScaled() * 16 - 4, be.getYSizeScaled() * 16 - 4);
        DLColor font = settings.getFontColor();
        Pair<Float,Float> vScale = Pair.of(BASE_SCALE, BASE_SCALE);
        Pair<Float,Float> hScale = Pair.of(BASE_H_SCALE_MIN, BASE_SCALE);

        for (int i = 0; i < COL_COUNT; i++) {
            BERLabel lbl = row[i] = new BERLabel();
            lbl.clippingArea.set(clip);
            lbl.verticalScale.set(vScale);
            lbl.horizontalScale.set(hScale);
            lbl.color.set(font);
        }
        row[COL_TRAIN_TEXT].backgroundPadding.set(new PaddingF(0.5f));
        row[COL_TRAIN_TEXT].horizontalScrollMode.set(EScrollMode.NEVER);
        row[COL_TIME].horizontalScrollMode.set(EScrollMode.NEVER);
        row[COL_DEST].horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
        row[COL_DEST].horizontalScrollingSpeed.set(SCROLLING_SPEED);
        row[COL_PLATFORM].horizontalAlign.set(ETextAlignment.RIGHT);
        row[COL_PLATFORM].horizontalScrollMode.set(EScrollMode.NEVER);
        row[COL_PLATFORM].backgroundPadding.set(new PaddingF(0.5f));
        return row;
    }

    // ── Pass 1: set text content ──────────────────────────────────────────
    private void populateText(BERLabel[] row, StationDisplayData stop, S settings, DLColor font) {
        BasicTrainDisplayData train   = stop.getTrainData();
        TrainStopDisplayData  station = stop.getStationData();
        EJRETrainTextMode     mode    = settings.getTrainTextMode();
        boolean               useETA  = settings.getTimeDisplay() == ETimeDisplay.ETA;
        boolean hasLineColor = train.hasColor(ETrainStopState.DEPARTURE);

        // Train text + background color
        String  trainStr;
        DLColor bgColor;
        switch (mode) {
            case LINE_NAME -> {
                trainStr = train.getName(ETrainStopState.DEPARTURE);
                bgColor  = settings.showColor() && hasLineColor
                    ? train.getColor(ETrainStopState.DEPARTURE) : DLColor.TRANSPARENT;
            }
            case CATEGORY_NAME -> {
                if (train instanceof IBasicTrainDisplayDataExt ext) {
                    trainStr = ext.crndisplaynext$getCategoryName(ETrainStopState.DEPARTURE);
                    bgColor  = settings.showColor() && ext.crndisplaynext$hasCategoryColor(ETrainStopState.DEPARTURE)
                        ? ext.crndisplaynext$getCategoryColor(ETrainStopState.DEPARTURE) : DLColor.TRANSPARENT;
                } else { trainStr = train.getName(ETrainStopState.DEPARTURE); bgColor = DLColor.TRANSPARENT; }
            }
            default -> { // TRAIN_NAME
                if (train instanceof IBasicTrainDisplayDataExt ext) {
                    String en = ext.crndisplaynext$getTrainEntityName();
                    trainStr  = (en != null && !en.isBlank()) ? en : station.getTrainName();
                    bgColor   = settings.showColor() && ext.crndisplaynext$hasCategoryColor(ETrainStopState.DEPARTURE)
                        ? ext.crndisplaynext$getCategoryColor(ETrainStopState.DEPARTURE) : DLColor.TRANSPARENT;
                } else { trainStr = station.getTrainName(); bgColor = DLColor.TRANSPARENT; }
            }
        }
        if (trainStr == null || trainStr.isBlank()) trainStr = "—";

        BERLabel ttLbl = row[COL_TRAIN_TEXT];
        ttLbl.text.set(TextUtils.text(trainStr));
        if (!bgColor.isTransparent()) {
            ttLbl.backgroundColor.set(bgColor);
            ttLbl.color.set(DLColor.pickBasedOnBrightness(bgColor, LIGHT_FONT_COLOR, DARK_FONT_COLOR, 0.5f));
        } else {
            ttLbl.backgroundColor.set(DLColor.TRANSPARENT);
            ttLbl.color.set(font);
        }

        // Time (with padding spaces for visual separation)
        row[COL_TIME].text.set(TextUtils.text(
            " " + ModUtils.formatTime(station.getScheduledDepartureTime(), useETA) + " "));
        row[COL_TIME].color.set(font);

        // Destination
        String dirSfx = loc("gui.crndisplaynext.jre.direction_suffix", "方面");
        row[COL_DEST].text.set(TextUtils.text(station.getDestination() + dirSfx));
        row[COL_DEST].color.set(font);

        // Platform
        if (showPlatform()) {
            String raw = station.getRealTimeStation().info().platform();
            String platSfx = loc("gui.crndisplaynext.jre.platform_suffix", "番線");
            row[COL_PLATFORM].text.set((raw != null && !raw.isBlank())
                ? TextUtils.text(raw + platSfx) : TextUtils.empty());
            row[COL_PLATFORM].color.set(font);
        } else {
            row[COL_PLATFORM].text.set(TextUtils.empty());
        }
    }

    // ── Compute column width (max across all rows for AUTO) ───────────────
    private float computeColWidth(byte configWidth, boolean isAuto, int col, int rowCount) {
        if (!isAuto) return Math.max(0, configWidth);
        float max = 0;
        for (BERLabel[] row : rows) {
            float w = row[col].getRenderedWidth();
            if (w > max) max = w;
        }
        return max + 1;
    }

    // ── Pass 3: position labels with uniform column widths ────────────────
    private void positionRow(BERLabel[] row, int index,
                              float leftOff, float ttW, float timeW,
                              float destMaxW, int totalW, float platW,
                              byte destConfig, boolean isAutoDestW, DLColor font) {
        float y = 3 + index * LINE_HEIGHT;
        float x = leftOff + 3;

        row[COL_TRAIN_TEXT].preferredWidth.set(ttW);
        row[COL_TRAIN_TEXT].position.set(Point.of(x, y));
        x += ttW;

        row[COL_TIME].preferredWidth.set(timeW);
        row[COL_TIME].position.set(Point.of(x, y));
        x += timeW;

        float destW = isAutoDestW ? destMaxW : Math.min(destConfig, destMaxW);
        row[COL_DEST].preferredWidth.set(Math.max(0, destW));
        row[COL_DEST].position.set(Point.of(x, y));

        if (showPlatform() && platW > 0) {
            row[COL_PLATFORM].preferredWidth.set(platW);
            row[COL_PLATFORM].position.set(Point.of(totalW - 3 - platW, y));
        } else {
            row[COL_PLATFORM].preferredWidth.set(0f);
        }
    }

    protected static String loc(String key, String fallback) {
        try { return CustomLanguage.translate(key).getString(); }
        catch (Exception e) { return fallback; }
    }
}
