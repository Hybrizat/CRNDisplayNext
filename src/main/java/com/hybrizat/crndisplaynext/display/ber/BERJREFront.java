package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import com.hybrizat.crndisplaynext.display.EJREFrontBContent;
import com.hybrizat.crndisplaynext.display.settings.JREFrontSettings;
import de.mrjulsen.crn.CreateRailwaysNavigator;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.crn.client.lang.CustomLanguage;
import com.hybrizat.crndisplaynext.compat.ETrainStopState;
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
 * JRE Front Destination Display (E233 style).
 *
 * Layout:
 *   [A: 種別/Category — color background] [B: 行先 or 線路名]
 *
 * A: category name with category color background (from IBasicTrainDisplayDataExt).
 *    Falls back to line color if category not configured.
 * B: switchable between destination (行先) and line name (線路).
 *
 * Both labels are vertically centered in the display block.
 * B label scrolls horizontally when text is too wide.
 */
public class BERJREFront implements AbstractAdvancedDisplayRenderer<JREFrontSettings> {

    private static final String KEY_OOS      = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.not_in_service";
    private static final String KEY_NO_BOARD = "block." + CreateRailwaysNavigator.MOD_ID + ".advanced_display.ber.do_not_board";

    private final BERLabel oosLabel  = new BERLabel();
    private final BERLabel aLabel    = new BERLabel(); // 種別
    private final BERLabel bLabel    = new BERLabel(); // 行先 or 線路

    public BERJREFront() {
        // A: category — bold, fixed-ish width, color background, centered
        aLabel.verticalScale.set(Pair.of(0.3f, 0.5f));
        aLabel.horizontalScale.set(Pair.of(0.2f, 0.5f));
        aLabel.horizontalScrollMode.set(EScrollMode.FLEX_FIT);
        aLabel.backgroundPadding.set(new PaddingF(1f));
        aLabel.horizontalAlign.set(ETextAlignment.CENTER);

        // B: destination / line — large, fills remaining width
        bLabel.verticalScale.set(Pair.of(0.3f, 0.5f));
        bLabel.horizontalScale.set(Pair.of(0.2f, 0.5f));
        bLabel.horizontalScrollMode.set(EScrollMode.WHEN_NEEDED);
        bLabel.horizontalScrollingSpeed.set(SCROLLING_SPEED);
        bLabel.horizontalAlign.set(ETextAlignment.CENTER);

        // OOS
        oosLabel.verticalScale.set(Pair.of(0.3f, 0.5f));
        oosLabel.horizontalScale.set(Pair.of(0.2f, 0.5f));
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
        aLabel.render(graphics, light);
        bLabel.render(graphics, light);
    }

    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity be,
                       AdvancedDisplayRenderInstance parent, EUpdateReason reason) {
        JREFrontSettings settings = getDisplaySettings(be);
        DLColor font   = settings.getFontColor();
        int w = be.getXSizeScaled() * 16;
        int h = be.getYSizeScaled() * 16;
        Rectangle clip = Rectangle.withSize(2, 2, w - 4, h - 4);
        float centerY  = h / 2f - 3f; // vertical center

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
            oosLabel.position.set(Point.of(3, centerY));
            return;
        }

        ETrainStopState stopState = ETrainStopState.beforeArrival(
            !com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).isWaitingAtStation());

        // ── A label: Category ───────────────────────────────────────────
        aLabel.clippingArea.set(clip);
        aLabel.glowing.set(be.isGlowing());

        var trainData = com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getTrainData();
        // A names the 種別 while the train stands (the service it is about to depart as)
        // and the line while it runs. Unlike the side display this flip is intended —
        // telling the service apart from outside is the front board's whole job.
        String categoryName = "";
        DLColor categoryColor = DLColor.TRANSPARENT;
        if (trainData instanceof IBasicTrainDisplayDataExt ext) {
            categoryName  = ext.crndisplaynext$getCategoryName(stopState);
            categoryColor = ext.crndisplaynext$getCategoryColor(stopState);
        }
        // crndisplaynext$getCategoryName answers with an EMPTY string when no 種別 is
        // configured — it has no blank guard, unlike getName — so this fallback is what
        // keeps the block showing the line name in that case. It is not dead code.
        if (categoryName.isBlank()) {
            categoryName  = trainData.getName(stopState);
            categoryColor = trainData.getColor(stopState);
        }

        aLabel.text.set(TextUtils.text(categoryName).withStyle(ChatFormatting.BOLD));
        if (settings.showLineColor() && !categoryColor.isTransparent()) {
            aLabel.backgroundColor.set(categoryColor);
            aLabel.color.set(DLColor.pickBasedOnBrightness(
                categoryColor, LIGHT_FONT_COLOR, DARK_FONT_COLOR, 0.5f));
        } else {
            aLabel.backgroundColor.set(DLColor.TRANSPARENT);
            aLabel.color.set(font);
        }

        // ── B label: Destination or Line ────────────────────────────────
        bLabel.clippingArea.set(clip);
        bLabel.glowing.set(be.isGlowing());
        bLabel.color.set(font);

        boolean bIsLineName = settings.getBContent() == EJREFrontBContent.LINE_NAME;
        String bText;
        if (bIsLineName) {
            // The option asks for the LINE name, so read the line name — not
            // getName(stopState), which answers with the 種別 while the train is standing
            // and made B an exact copy of the block on its left.
            bText = trainData.getLineName();
            // While running, A already shows the line name, and printing it twice side by
            // side is the duplication this guards against. Comparing the rendered strings
            // rather than a flag derived from which branch filled A keeps it right for
            // every combination of configured 種別/线路.
            if (bText.equals(categoryName)) {
                bText = "";
            }
        } else {
            bText = com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getCurrentStop().isPresent()
                ? com.hybrizat.crndisplaynext.compat.CrnDisplayAdapter.train(be).getCurrentStop().get().getDestination() : "";
        }
        bLabel.text.set(TextUtils.text(bText).withStyle(ChatFormatting.BOLD));

        boolean bEmpty = bText.isEmpty();
        if (bEmpty) {
            // B empty → A centered across the full display width
            aLabel.preferredWidth.set((float)(w - 6));
            bLabel.position.set(Point.of(w, centerY));
            bLabel.preferredWidth.set(0f);
        } else {
            aLabel.preferredWidth.set(Math.min(w / 3f, aLabel.getRenderedWidth() + 4));
            float bX = 3 + aLabel.preferredWidth.get() + 3;
            bLabel.position.set(Point.of(bX, centerY));
            bLabel.preferredWidth.set(w - 3 - bX);
        }
        aLabel.position.set(Point.of(3, centerY));
    }
}
