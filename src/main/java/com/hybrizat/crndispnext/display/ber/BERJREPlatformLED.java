package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import com.hybrizat.crndisplaynext.display.EJRETrainTextMode;
import com.hybrizat.crndisplaynext.display.settings.JREPlatformLEDSettings;
import de.mrjulsen.crn.CreateRailwaysNavigator;
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

/**
 * JRE LED Style — multi-line platform departure board.
 *
 * Layout per row (mirrors BERPlatformDetailed structure):
 *   [TIME]  [TRAIN_NAME or CATEGORY]  [DESTINATION]方面  [PLATFORM]番線
 *
 * Each row uses three BERLabel components:
 *   - timeLabel:        scheduled departure time
 *   - trainTextLabel:   train name or category name, with color background
 *   - destinationLabel: destination + direction suffix, scrolls if too wide
 *   - platformLabel:    platform number, right-aligned (hidden if empty)
 *
 * Color background logic (mirrors BERPlatformDetailed.updateContent):
 *   TRAIN_NAME mode:    backgroundColor = line color    (trainData.getColor())
 *   CATEGORY_NAME mode: backgroundColor = category color (IBasicTrainDisplayDataExt)
 *   Font color auto-selected via DLColor.pickBasedOnBrightness() for contrast.
 */
public class BERJREPlatformLED implements AbstractAdvancedDisplayRenderer<JREPlatformLEDSettings> {

    private static final float LINE_HEIGHT = 5.4f;
    private static final float TIME_WIDTH  = 14f;

    // Per-row label arrays  [row][component]
    private BERLabel[][] rows = new BERLabel[0][];

    // ── tick: update clipping area and glow every frame ───────────────────
    @Override
    public void tick(Level level, BlockPos pos, BlockState state,
                     AdvancedDisplayBlockEntity blockEntity,
                     AdvancedDisplayRenderInstance parent) {

        Rectangle clip = Rectangle.withSize(2, 2,
            blockEntity.getXSizeScaled() * 16 - 4,
            blockEntity.getYSizeScaled() * 16 - 4);
        boolean glowing = blockEntity.isGlowing();

        for (BERLabel[] row : rows) {
            for (BERLabel lbl : row) {
                lbl.clippingArea.set(clip);
                lbl.glowing.set(glowing);
            }
        }
    }

    // ── render ─────────────────────────────────────────────────────────────
    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics,
                       float partialTick, AdvancedDisplayRenderInstance parent,
                       int light, boolean backSide) {
        for (BERLabel[] row : rows) {
            for (BERLabel lbl : row) {
                lbl.render(graphics, light);
            }
        }
    }

    // ── update ─────────────────────────────────────────────────────────────
    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity blockEntity,
                       AdvancedDisplayRenderInstance parent,
                       EUpdateReason reason) {

        if (!blockEntity.isController()) {
            rows = new BERLabel[0][];
            return;
        }

        JREPlatformLEDSettings settings = getDisplaySettings(blockEntity);
        int maxRows = blockEntity.getYSizeScaled() * 3 - 1;

        // Filter stops — same logic as BERPlatformDetailed
        List<StationDisplayData> departures = new ArrayList<>();
        for (StationDisplayData data : blockEntity.getStops()) {
            if (data.isNextSectionExcluded()) continue;
            boolean cancelled   = data.getTrainData().isCancelled();
            boolean stillValid  = ModUtils.getTransformedWorldTime()
                < data.getStationData().getScheduledDepartureTime()
                + ModCommonConfig.DISPLAY_LEAD_TIME.get();
            if (!cancelled || stillValid) {
                departures.add(data);
            }
            if (departures.size() >= maxRows) break;
        }

        // Rebuild label grid if layout changed
        int count = departures.size();
        if (reason == EUpdateReason.LAYOUT_CHANGED
                || rows == null || rows.length != count) {
            rows = new BERLabel[count][];
            for (int i = 0; i < count; i++) {
                rows[i] = createRow(blockEntity, settings);
            }
        }

        // Populate content
        for (int i = 0; i < count; i++) {
            updateRow(rows[i], departures.get(i), i, blockEntity, settings);
        }
    }

    // ── Row creation ───────────────────────────────────────────────────────
    private static final int COL_TIME        = 0;
    private static final int COL_TRAIN_TEXT  = 1;
    private static final int COL_DESTINATION = 2;
    private static final int COL_PLATFORM    = 3;

    private BERLabel[] createRow(AdvancedDisplayBlockEntity be,
                                  JREPlatformLEDSettings settings) {
        BERLabel[] row = new BERLabel[4];
        Rectangle clip = Rectangle.withSize(2, 2,
            be.getXSizeScaled() * 16 - 4,
            be.getYSizeScaled() * 16 - 4);
        DLColor fontColor = settings.getFontColor();
        Pair<Float,Float> scale = Pair.of(0.4f, 0.4f);

        // Time
        BERLabel time = row[COL_TIME] = new BERLabel();
        time.clippingArea.set(clip);
        time.verticalScale.set(scale);
        time.horizontalScale.set(Pair.of(0.2f, 0.4f));
        time.preferredWidth.set(TIME_WIDTH);
        time.horizontalScrollMode.set(EScrollMode.NEVER);
        time.color.set(fontColor);

        // Train text (name or category) — may have color background
        BERLabel trainText = row[COL_TRAIN_TEXT] = new BERLabel();
        trainText.clippingArea.set(clip);
        trainText.verticalScale.set(scale);
        trainText.horizontalScale.set(Pair.of(0.2f, 0.4f));
        trainText.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
        trainText.horizontalScrollingSpeed.set(SCROLLING_SPEED);
        trainText.backgroundPadding.set(new PaddingF(0.5f));
        trainText.color.set(fontColor);

        // Destination
        BERLabel dest = row[COL_DESTINATION] = new BERLabel();
        dest.clippingArea.set(clip);
        dest.verticalScale.set(scale);
        dest.horizontalScale.set(Pair.of(0.2f, 0.4f));
        dest.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
        dest.horizontalScrollingSpeed.set(SCROLLING_SPEED);
        dest.color.set(fontColor);

        // Platform (right-aligned)
        BERLabel platform = row[COL_PLATFORM] = new BERLabel();
        platform.clippingArea.set(clip);
        platform.verticalScale.set(scale);
        platform.horizontalScale.set(Pair.of(0.2f, 0.4f));
        platform.horizontalScrollMode.set(EScrollMode.NEVER);
        platform.horizontalAlign.set(ETextAlignment.RIGHT);
        platform.color.set(fontColor);

        return row;
    }

    // ── Row content update ─────────────────────────────────────────────────
    private void updateRow(BERLabel[] row, StationDisplayData stop, int index,
                            AdvancedDisplayBlockEntity be,
                            JREPlatformLEDSettings settings) {

        BasicTrainDisplayData   trainData   = stop.getTrainData();
        TrainStopDisplayData    stationData = stop.getStationData();
        EJRETrainTextMode       textMode    = settings.getTrainTextMode();
        boolean                 useETA      = settings.getTimeDisplay() == ETimeDisplay.ETA;
        DLColor                 fontColor   = settings.getFontColor();
        int                     w           = be.getXSizeScaled() * 16;
        float                   y           = 3 + index * LINE_HEIGHT;

        // ── Time ─────────────────────────────────────────────────────────
        BERLabel timeLabel = row[COL_TIME];
        timeLabel.text.set(TextUtils.text(
            ModUtils.formatTime(stationData.getScheduledDepartureTime(), useETA)));
        timeLabel.color.set(fontColor);
        timeLabel.position.set(Point.of(3, y));

        // ── Train text + color background ─────────────────────────────────
        BERLabel trainTextLabel = row[COL_TRAIN_TEXT];

        String  trainTextStr;
        DLColor bgColor;

        if (textMode == EJRETrainTextMode.CATEGORY_NAME
                && trainData instanceof IBasicTrainDisplayDataExt ext
                && ext.crndisplaynext$hasCategoryColor(ETrainStopState.DEPARTURE)) {
            // Category mode: use category name + category color background
            trainTextStr = ext.crndisplaynext$getCategoryName(ETrainStopState.DEPARTURE);
            bgColor      = settings.showLineColor()
                ? ext.crndisplaynext$getCategoryColor(ETrainStopState.DEPARTURE)
                : DLColor.TRANSPARENT;
        } else {
            // Train name mode: use CRN-resolved display name + line color background
            trainTextStr = trainData.getName(ETrainStopState.DEPARTURE);
            bgColor      = settings.showLineColor()
                    && trainData.hasColor(ETrainStopState.DEPARTURE)
                ? trainData.getColor(ETrainStopState.DEPARTURE)
                : DLColor.TRANSPARENT;
        }

        trainTextLabel.text.set(TextUtils.text(trainTextStr));
        if (!bgColor.isTransparent()) {
            // Color background: auto-select font color for contrast
            trainTextLabel.backgroundColor.set(bgColor);
            trainTextLabel.color.set(DLColor.pickBasedOnBrightness(
                bgColor, LIGHT_FONT_COLOR, DARK_FONT_COLOR, 0.5f));
        } else {
            trainTextLabel.backgroundColor.set(DLColor.TRANSPARENT);
            trainTextLabel.color.set(fontColor);
        }

        float trainTextX = 3 + TIME_WIDTH + 2;
        trainTextLabel.position.set(Point.of(trainTextX, y));
        trainTextLabel.preferredWidth.set(trainTextLabel.getRenderedWidth() + 2);

        // ── Platform (right-aligned, hidden if blank) ─────────────────────
        BERLabel platformLabel = row[COL_PLATFORM];
        String platformStr = stationData.getRealTimeStation().info().platform();
        boolean hasPlatform = platformStr != null && !platformStr.isBlank();

        String platformSuffix;
        try { platformSuffix = CustomLanguage.translate("gui.crndisplaynext.jre.platform_suffix").getString(); }
        catch (Exception e) { platformSuffix = "番線"; }

        platformLabel.text.set(hasPlatform
            ? TextUtils.text(platformStr + platformSuffix)
            : TextUtils.empty());
        platformLabel.color.set(fontColor);
        float platformWidth = hasPlatform ? platformLabel.getRenderedWidth() + 1 : 0;
        platformLabel.preferredWidth.set(platformWidth);
        platformLabel.position.set(Point.of(w - 3 - platformWidth, y));

        // ── Destination (fills remaining space) ───────────────────────────
        BERLabel destLabel = row[COL_DESTINATION];
        String dirSuffix;
        try { dirSuffix = CustomLanguage.translate("gui.crndisplaynext.jre.direction_suffix").getString(); }
        catch (Exception e) { dirSuffix = "方面"; }

        destLabel.text.set(TextUtils.text(
            stationData.getDestination() + dirSuffix));
        destLabel.color.set(fontColor);

        float destX     = trainTextX + trainTextLabel.preferredWidth.get() + 2;
        float destWidth = w - 3 - destX - (hasPlatform ? platformWidth + 3 : 0);
        destLabel.position.set(Point.of(destX, y));
        destLabel.preferredWidth.set(Math.max(0, destWidth));
    }
}
