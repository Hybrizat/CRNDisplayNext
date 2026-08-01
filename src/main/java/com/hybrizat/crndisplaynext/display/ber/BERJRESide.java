package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import com.hybrizat.crndisplaynext.display.EJRESideMode;
import com.hybrizat.crndisplaynext.display.settings.JRESideSettings;
import de.mrjulsen.crn.CreateRailwaysNavigator;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.crn.client.lang.CustomLanguage;
import de.mrjulsen.crn.data.train.ETrainStopState;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * JRE Side Destination Display (E233 style).
 *
 * NEXT_STOP mode (2-row, left-right split):
 *   ┌──────┬──────────────────┐
 *   │[種別]│ [行先]            │
 *   │ 色块 │ 次は [次の駅]     │
 *   └──────┴──────────────────┘
 *
 * DEST mode (1-row):
 *   [種別] [行先]
 *
 * LINE mode (1-row):
 *   [種別] [線路名]
 */
public class BERJRESide implements AbstractAdvancedDisplayRenderer<JRESideSettings> {

    private static final String KEY_OOS      = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.not_in_service";
    private static final String KEY_NO_BOARD = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.do_not_board";
    private static final String KEY_NEXT     = "gui.crndisplaynext.jre.next_stop_prefix"; // "次は"

    private final BERLabel oosLabel      = new BERLabel();
    private final BERLabel categoryLabel = new BERLabel(); // A: 種別
    private final BERLabel mainLabel     = new BERLabel(); // B: 行先 or 線路
    private final BERLabel nextLabel     = new BERLabel(); // "次は ○○" (NEXT_STOP mode only)

    public BERJRESide() {
        Pair<Float,Float> vScale = Pair.of(0.25f, 0.5f);
        Pair<Float,Float> hScale = Pair.of(0.2f, 0.5f);

        categoryLabel.verticalScale.set(vScale);
        categoryLabel.horizontalScale.set(hScale);
        categoryLabel.horizontalScrollMode.set(EScrollMode.FLEX_FIT);
        categoryLabel.backgroundPadding.set(new PaddingF(0.5f));
        categoryLabel.horizontalAlign.set(ETextAlignment.CENTER);

        mainLabel.verticalScale.set(vScale);
        mainLabel.horizontalScale.set(hScale);
        mainLabel.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
        mainLabel.horizontalScrollingSpeed.set(SCROLLING_SPEED);

        nextLabel.verticalScale.set(Pair.of(0.2f, 0.35f));
        nextLabel.horizontalScale.set(Pair.of(0.15f, 0.35f));
        nextLabel.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
        nextLabel.horizontalScrollingSpeed.set(SCROLLING_SPEED);

        oosLabel.verticalScale.set(vScale);
        oosLabel.horizontalScale.set(hScale);
        oosLabel.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
        oosLabel.horizontalAlign.set(ETextAlignment.CENTER);
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
        mainLabel.render(graphics, light);
        if (getDisplaySettings(graphics.blockEntity()).getSideMode() == EJRESideMode.NEXT_STOP) {
            nextLabel.render(graphics, light);
        }
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity be,
                       AdvancedDisplayRenderInstance parent, EUpdateReason reason) {
        JRESideSettings settings = getDisplaySettings(be);
        DLColor font   = settings.getFontColor();
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
            return;
        }

        ETrainStopState stopState = ETrainStopState.beforeArrival(
            !be.getTrainData().isWaitingAtStation());
        EJRESideMode mode = settings.getSideMode();

        // ── Category (A) ────────────────────────────────────────────────
        categoryLabel.clippingArea.set(clip);
        categoryLabel.glowing.set(be.isGlowing());

        String catName  = "";
        DLColor catColor = DLColor.TRANSPARENT;
        boolean catIsLineName = false;
        if (be.getTrainData().getTrainData() instanceof IBasicTrainDisplayDataExt ext) {
            catName  = ext.crndisplaynext$getCategoryName(stopState);
            catColor = ext.crndisplaynext$getCategoryColor(stopState);
        }
        if (catName.isBlank()) {
            catName  = be.getTrainData().getTrainData().getName(stopState);
            catColor = be.getTrainData().getTrainData().getColor(stopState);
            catIsLineName = true; // category area already shows the line name
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

        float catW = Math.min(w / 3f, categoryLabel.getRenderedWidth() + 4);
        categoryLabel.preferredWidth.set(catW);
        float bX = 3 + catW + 3;

        // ── Main (B) and Next label ──────────────────────────────────────
        mainLabel.clippingArea.set(clip);
        mainLabel.glowing.set(be.isGlowing());
        mainLabel.color.set(font);
        nextLabel.clippingArea.set(clip);
        nextLabel.glowing.set(be.isGlowing());
        nextLabel.color.set(font);

        String dest = be.getTrainData().getCurrentStop().isPresent()
            ? be.getTrainData().getCurrentStop().get().getDestination() : "";
        String lineName = be.getTrainData().getTrainData().getName(stopState);

        if (mode == EJRESideMode.NEXT_STOP) {
            // Left column: category spans full height, centered vertically
            float catY = h / 2f - 3f;
            categoryLabel.position.set(Point.of(3, catY));

            // Right top: destination
            mainLabel.text.set(TextUtils.text(dest).withStyle(ChatFormatting.BOLD));
            mainLabel.position.set(Point.of(bX, h * 0.25f - 2f));
            mainLabel.preferredWidth.set(w - 3 - bX);

            // Right bottom: "次は ○○"
            String nextStopName = be.getTrainData().getNextStop().isPresent()
                ? be.getTrainData().getNextStop().get().getRealTimeStation().tagName() : "";
            String prefix = loc(KEY_NEXT, "次は ");
            nextLabel.text.set(TextUtils.text(prefix + nextStopName));
            nextLabel.position.set(Point.of(bX, h * 0.6f - 1f));
            nextLabel.preferredWidth.set(w - 3 - bX);

        } else {
            // Single row: category left, main right, vertically centered
            float rowY = h / 2f - 3f;
            boolean mainEmpty = (mode == EJRESideMode.LINE) && catIsLineName;
            if (mainEmpty) {
                // B empty → category centered across the full display width
                categoryLabel.preferredWidth.set((float)(w - 6));
                categoryLabel.position.set(Point.of(3, rowY));
                mainLabel.text.set(TextUtils.empty());
            } else {
                categoryLabel.preferredWidth.set(catW);
                categoryLabel.position.set(Point.of(3, rowY));
                mainLabel.position.set(Point.of(bX, rowY));
                mainLabel.preferredWidth.set(w - 3 - bX);
                if (mode == EJRESideMode.LINE) {
                    mainLabel.text.set(TextUtils.text(lineName).withStyle(ChatFormatting.BOLD));
                } else {
                    mainLabel.text.set(TextUtils.text(dest).withStyle(ChatFormatting.BOLD));
                }
            }
            nextLabel.text.set(TextUtils.empty());
        }
    }

    private static String loc(String key, String fallback) {
        try { return CustomLanguage.translate(key).getString(); }
        catch (Exception e) { return fallback; }
    }
}
