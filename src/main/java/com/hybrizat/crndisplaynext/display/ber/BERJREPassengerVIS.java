package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import com.hybrizat.crndisplaynext.display.settings.JREVISSettings;
import de.mrjulsen.crn.CreateRailwaysNavigator;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.block.properties.ETimeDisplay;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.crn.client.lang.CustomLanguage;
import de.mrjulsen.crn.config.ModClientConfig;
import de.mrjulsen.crn.data.TrainExitSide;
import de.mrjulsen.crn.data.train.ETrainStopState;
import de.mrjulsen.crn.data.train.portable.TrainDisplayData;
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
import de.mrjulsen.mcdragonlib.util.time.DLTime;
import de.mrjulsen.mcdragonlib.util.time.TimeContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * JRE Passenger VIS (Visual Information System) — E235/E233 LCD style.
 *
 * Header bar (always visible):
 *   [線路/種別 — color bg]  [行先 — large]  [時刻]
 *
 * Body switches between two pages on a timer:
 *   Page 1 — Stop list (current → terminus):
 *     ○ HH:MM  Station Name   (bold if terminus)
 *   Page 2 — Stats (speed, date, carriage number)
 *            shown briefly when not near next stop
 *
 * "Next stop" announcement mode (triggered when close to next station):
 *   Header: "次は ○○"  [exit arrow if configured]
 */
public class BERJREPassengerVIS implements AbstractAdvancedDisplayRenderer<JREVISSettings> {

    private static final String KEY_OOS      = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.not_in_service";
    private static final String KEY_NO_BOARD = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.do_not_board";
    private static final String KEY_NEXT     = "gui.crndisplaynext.jre.next_stop_prefix";
    private static final String KEY_TERMINUS = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.train_terminates";

    private static final float HEADER_H   = 5.5f;
    private static final float LINE_H     = 2.2f;
    private static final float MAX_LINES  = 4;

    // Header
    private final BERLabel oosLabel      = new BERLabel();
    private final BERLabel categoryLabel = new BERLabel();
    private final BERLabel destLabel     = new BERLabel();
    private final BERLabel timeLabel     = new BERLabel();

    // Stop list
    private BERLabel[] stopTimeLabels = new BERLabel[0];
    private BERLabel[] stopNameLabels = new BERLabel[0];

    public BERJREPassengerVIS() {
        categoryLabel.verticalScale.set(Pair.of(0.25f, 0.4f));
        categoryLabel.horizontalScale.set(Pair.of(0.15f, 0.4f));
        categoryLabel.horizontalScrollMode.set(EScrollMode.FLEX_FIT);
        categoryLabel.backgroundPadding.set(new PaddingF(0.5f));
        categoryLabel.horizontalAlign.set(ETextAlignment.CENTER);

        destLabel.verticalScale.set(Pair.of(0.25f, 0.4f));
        destLabel.horizontalScale.set(Pair.of(0.15f, 0.4f));
        destLabel.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
        destLabel.horizontalScrollingSpeed.set(SCROLLING_SPEED);

        timeLabel.verticalScale.set(Pair.of(0.2f, 0.3f));
        timeLabel.horizontalScale.set(Pair.of(0.15f, 0.3f));
        timeLabel.horizontalAlign.set(ETextAlignment.RIGHT);
        timeLabel.horizontalScrollMode.set(EScrollMode.NEVER);

        oosLabel.verticalScale.set(Pair.of(0.25f, 0.4f));
        oosLabel.horizontalScale.set(Pair.of(0.15f, 0.4f));
        oosLabel.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
        oosLabel.horizontalAlign.set(ETextAlignment.CENTER);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state,
                     AdvancedDisplayBlockEntity be, AdvancedDisplayRenderInstance parent) {
        // Update clock every tick
        String time = new DLTime(be.getLevel(), DLTime.defaultTimeSystem())
            .format(ModClientConfig.TIME_FORMAT.get().getFormat(),
                    TimeContext.INGAME, DLTime.defaultTimeSystem());
        timeLabel.text.set(TextUtils.text(time));
        timeLabel.color.set(getDisplaySettings(be).getFontColor());

        Rectangle clip = Rectangle.withSize(2, 2,
            be.getXSizeScaled() * 16 - 4, be.getYSizeScaled() * 16 - 4);
        boolean glow = be.isGlowing();
        timeLabel.clippingArea.set(clip);
        timeLabel.glowing.set(glow);
        for (BERLabel lbl : stopTimeLabels) { lbl.clippingArea.set(clip); lbl.glowing.set(glow); }
        for (BERLabel lbl : stopNameLabels) { lbl.clippingArea.set(clip); lbl.glowing.set(glow); }
    }

    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics,
                       float partial, AdvancedDisplayRenderInstance parent,
                       int light, boolean backSide) {
        boolean oos = graphics.blockEntity().getTrainData() == null
            || graphics.blockEntity().getTrainData().getState()
               .isIrregular(getDisplaySettings(graphics.blockEntity()).showDoNotBoardText());
        if (oos) { oosLabel.render(graphics, light); return; }

        categoryLabel.render(graphics, light);
        destLabel.render(graphics, light);
        timeLabel.render(graphics, light);

        for (int i = 0; i < stopTimeLabels.length; i++) {
            stopTimeLabels[i].render(graphics, light);
            stopNameLabels[i].render(graphics, light);
        }
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity be,
                       AdvancedDisplayRenderInstance parent, EUpdateReason reason) {
        JREVISSettings settings = getDisplaySettings(be);
        DLColor font = settings.getFontColor();
        int w = be.getXSizeScaled() * 16;
        int h = be.getYSizeScaled() * 16;
        Rectangle clip = Rectangle.withSize(2, 2, w - 4, h - 4);

        oosLabel.clippingArea.set(clip);
        oosLabel.glowing.set(be.isGlowing());
        oosLabel.color.set(font);

        boolean oos = be.getTrainData() == null
            || be.getTrainData().getState().isIrregular(settings.showDoNotBoardText());
        if (oos) {
            boolean noBoard = be.getTrainData() != null
                && be.getTrainData().getState().shouldNotBoard(settings.showDoNotBoardText());
            oosLabel.text.set(noBoard
                ? CustomLanguage.translate(KEY_NO_BOARD)
                : CustomLanguage.translate(KEY_OOS));
            oosLabel.preferredWidth.set((float)(w - 6));
            oosLabel.position.set(Point.of(3, h / 2f - 3f));
            stopTimeLabels = new BERLabel[0];
            stopNameLabels = new BERLabel[0];
            return;
        }

        TrainDisplayData data = be.getTrainData();
        ETrainStopState stopState = ETrainStopState.beforeArrival(!data.isWaitingAtStation());

        // ── Category label ───────────────────────────────────────────────
        categoryLabel.clippingArea.set(clip);
        categoryLabel.glowing.set(be.isGlowing());

        String catName  = "";
        DLColor catColor = DLColor.TRANSPARENT;
        if (data.getTrainData() instanceof IBasicTrainDisplayDataExt ext) {
            catName  = ext.crndisplaynext$getCategoryName(stopState);
            catColor = ext.crndisplaynext$getCategoryColor(stopState);
        }
        if (catName.isBlank()) {
            catName  = data.getTrainData().getName(stopState);
            catColor = data.getTrainData().getColor(stopState);
        }

        categoryLabel.text.set(TextUtils.text(catName).withStyle(ChatFormatting.BOLD));
        if (settings.showLineColor() && !catColor.isTransparent()) {
            categoryLabel.backgroundColor.set(catColor);
            categoryLabel.color.set(DLColor.pickBasedOnBrightness(
                catColor, LIGHT_FONT_COLOR, DARK_FONT_COLOR, 0.5f));
        } else {
            categoryLabel.backgroundColor.set(DLColor.TRANSPARENT);
            categoryLabel.color.set(font);
        }
        float catW = Math.min(w / 4f, categoryLabel.getRenderedWidth() + 4);
        categoryLabel.preferredWidth.set(catW);
        categoryLabel.position.set(Point.of(3, 2));

        // ── Time label (right-aligned in header) ─────────────────────────
        timeLabel.clippingArea.set(clip);
        timeLabel.glowing.set(be.isGlowing());
        float timeW = timeLabel.getRenderedWidth() + 2;
        timeLabel.preferredWidth.set(timeW);
        timeLabel.position.set(Point.of(w - 3 - timeW, 2));

        // ── Destination label ────────────────────────────────────────────
        destLabel.clippingArea.set(clip);
        destLabel.glowing.set(be.isGlowing());
        destLabel.color.set(font);

        String dest = data.getCurrentStop().isPresent()
            ? data.getCurrentStop().get().getDestination() : "";
        destLabel.text.set(TextUtils.text(dest).withStyle(ChatFormatting.BOLD));
        float destX = 3 + catW + 3;
        destLabel.position.set(Point.of(destX, 2));
        destLabel.preferredWidth.set(w - 3 - destX - timeW - 3);

        // ── Stop list ────────────────────────────────────────────────────
        List<TrainStopDisplayData> stops = data.getStopsFromCurrentStation();
        int lineCount = (int) Math.min(MAX_LINES, Math.min(stops.size(),
            (h - HEADER_H - 4) / LINE_H));

        if (stopTimeLabels.length != lineCount) {
            stopTimeLabels = new BERLabel[lineCount];
            stopNameLabels = new BERLabel[lineCount];
            for (int i = 0; i < lineCount; i++) {
                BERLabel tl = stopTimeLabels[i] = new BERLabel();
                tl.verticalScale.set(Pair.of(0.15f, 0.2f));
                tl.horizontalScale.set(Pair.of(0.1f, 0.2f));
                tl.horizontalScrollMode.set(EScrollMode.NEVER);
                tl.preferredWidth.set(8f);

                BERLabel nl = stopNameLabels[i] = new BERLabel();
                nl.verticalScale.set(Pair.of(0.15f, 0.2f));
                nl.horizontalScale.set(Pair.of(0.1f, 0.2f));
                nl.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
                nl.horizontalScrollingSpeed.set(SCROLLING_SPEED);
            }
        }

        boolean useETA = settings.getTimeDisplay() == ETimeDisplay.ETA;
        for (int i = 0; i < lineCount; i++) {
            int idx = i >= lineCount - 1 ? stops.size() - 1 : i;
            TrainStopDisplayData stop = stops.get(idx);
            boolean isTerminus = (idx == stops.size() - 1);
            float lineY = HEADER_H + 2 + i * LINE_H;

            stopTimeLabels[i].clippingArea.set(clip);
            stopTimeLabels[i].glowing.set(be.isGlowing());
            stopTimeLabels[i].text.set(TextUtils.text(
                ModUtils.formatTime(stop.getScheduledArrivalTime(), useETA)));
            stopTimeLabels[i].color.set(font);
            stopTimeLabels[i].position.set(Point.of(3, lineY));

            stopNameLabels[i].clippingArea.set(clip);
            stopNameLabels[i].glowing.set(be.isGlowing());
            stopNameLabels[i].text.set(TextUtils.text(
                stop.getRealTimeStation().tagName())
                .withStyle(isTerminus ? ChatFormatting.BOLD : ChatFormatting.RESET));
            stopNameLabels[i].color.set(font);
            stopNameLabels[i].position.set(Point.of(13, lineY));
            stopNameLabels[i].preferredWidth.set((float)(w - 16));
        }
    }
}
