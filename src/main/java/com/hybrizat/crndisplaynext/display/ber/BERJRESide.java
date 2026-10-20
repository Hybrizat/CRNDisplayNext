package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.display.EJRESideMode;
import com.hybrizat.crndisplaynext.display.settings.JRESideSettings;
import de.mrjulsen.crn.CreateRailwaysNavigator;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.crn.client.lang.CustomLanguage;
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
 * The left colour block always names the LINE (falling back to the 種別 only when the
 * train has no line name at all) and never changes with the dwell state, so the display
 * keeps the same meaning from the moment the train arrives to the moment it leaves.
 *
 * NEXT_STOP mode (2-row, left-right split):
 *   ┌──────┬──────────────────┐
 *   │[線路名]│ [行先]            │
 *   │ 色块  │ 次は [次の駅]     │
 *   └──────┴──────────────────┘
 *
 * DEST mode (1-row):
 *   [線路名] [行先]
 *
 * LINE mode (1-row):
 *   [線路名] — the block spans the full width, since B would only repeat it
 */
public class BERJRESide implements AbstractAdvancedDisplayRenderer<JRESideSettings> {

    private static final String KEY_OOS      = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.not_in_service";
    private static final String KEY_NO_BOARD = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.do_not_board";
    private static final String KEY_NEXT     = "gui.crndisplaynext.jre.next_stop_prefix"; // "次は"

    private final BERLabel oosLabel      = new BERLabel();
    private final BERLabel categoryLabel = new BERLabel(); // A: 線路名 (fallback 種別)
    private final BERLabel mainLabel     = new BERLabel(); // B: 行先
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
        boolean oos = com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(graphics.blockEntity()) == null
            || com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(graphics.blockEntity()).getState()
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

        boolean oos = com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be) == null
            || com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getState().isIrregular(settings.showDoNotBoardText());
        if (oos) {
            boolean noBoard = com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be) != null
                && com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getState().shouldNotBoard(settings.showDoNotBoardText());
            oosLabel.text.set(noBoard
                ? CustomLanguage.translate(KEY_NO_BOARD)
                : CustomLanguage.translate(KEY_OOS));
            oosLabel.preferredWidth.set((float)(w - 6));
            oosLabel.position.set(Point.of(3, h / 2f - 3f));
            return;
        }

        EJRESideMode mode = settings.getSideMode();

        // ── Line (A) ───────────────────────────────────────────────────
        // The left colour block names the LINE and nothing else, and must not change
        // what it means when the train comes to a stand.
        //
        // It used to read crndisplaynext$getCategoryName(beforeArrival(!dwelling)), which
        // yields the 種別 while dwelling and the line name while running — the display
        // silently changed identity on every arrival. ETrainStopState only encodes CRN's
        // old arrival/departure distinction and means nothing for a destination board, so
        // it is not consulted here at all.
        categoryLabel.clippingArea.set(clip);
        categoryLabel.glowing.set(be.isGlowing());

        var trainData = com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getTrainData();
        String lineName = trainData.getLineName();
        // A train with no line still needs the block to say something, so fall back to
        // its 種別 rather than leaving a blank cell.
        String catName   = lineName.isBlank() ? trainData.getCategoryName() : lineName;
        DLColor catColor = lineName.isBlank() ? trainData.getCategoryColor() : trainData.getLineColor();

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

        String dest = com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getCurrentStop().isPresent()
            ? com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getCurrentStop().get().getDestination() : "";

        if (mode == EJRESideMode.NEXT_STOP) {
            // Left column: line name spans full height, centered vertically
            float catY = h / 2f - 3f;
            categoryLabel.position.set(Point.of(3, catY));

            // Right top: destination
            mainLabel.text.set(TextUtils.text(dest).withStyle(ChatFormatting.BOLD));
            mainLabel.position.set(Point.of(bX, h * 0.25f - 2f));
            mainLabel.preferredWidth.set(w - 3 - bX);

            // Right bottom: "次は ○○"
            String nextStopName = com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getNextStop().isPresent()
                ? com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getNextStop().get().getRealTimeStation().displayName() : "";
            String prefix = loc(KEY_NEXT, "次は ");
            nextLabel.text.set(TextUtils.text(prefix + nextStopName));
            nextLabel.position.set(Point.of(bX, h * 0.6f - 1f));
            nextLabel.preferredWidth.set(w - 3 - bX);

        } else {
            // Single row: line left, main right, vertically centered
            float rowY = h / 2f - 3f;
            // LINE mode asks for exactly what the left block already shows, so B stays
            // empty and the line is centred across the whole display. The old guard
            // tested a flag that was only set when no 種別 was configured, so with a
            // 種別 present both labels rendered the same string side by side.
            boolean mainEmpty = (mode == EJRESideMode.LINE);
            if (mainEmpty) {
                categoryLabel.preferredWidth.set((float)(w - 6));
                categoryLabel.position.set(Point.of(3, rowY));
                mainLabel.text.set(TextUtils.empty());
            } else {
                categoryLabel.preferredWidth.set(catW);
                categoryLabel.position.set(Point.of(3, rowY));
                mainLabel.position.set(Point.of(bX, rowY));
                mainLabel.preferredWidth.set(w - 3 - bX);
                mainLabel.text.set(TextUtils.text(dest).withStyle(ChatFormatting.BOLD));
            }
            nextLabel.text.set(TextUtils.empty());
        }
    }

    private static String loc(String key, String fallback) {
        try { return CustomLanguage.translate(key).getString(); }
        catch (Exception e) { return fallback; }
    }
}
