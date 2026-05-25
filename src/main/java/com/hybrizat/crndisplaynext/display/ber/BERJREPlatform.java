package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.display.settings.JREPlatformSettings;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.data.train.TrainUtils;
import de.mrjulsen.crn.data.train.portable.StationDisplayData;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel;
import de.mrjulsen.mcdragonlib.util.Pair;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Point;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * JRE Platform display — no platform number column, optional direction arrows.
 *
 * Arrow rendering:
 *   Left arrow  (←) is placed at x = 3 (leftmost), rendered BEFORE TrainText.
 *   Right arrow (→) is placed at x = totalW - 3 - ARROW_WIDTH (rightmost).
 *   AbstractJRERenderer reserves space via arrowReservedLeft/Right so content
 *   columns never overlap the arrow columns.
 *
 * Double-sided:
 *   When backSide=true, ← and → are swapped so the arrow always points
 *   toward the correct physical side regardless of which face is being rendered.
 *
 * Matching:
 *   For each row, station tag name and platform number are compared against
 *   leftPlatform / rightPlatform filters using TrainUtils.stationMatches()
 *   (supports * wildcards).
 */
public class BERJREPlatform extends AbstractJRERenderer<JREPlatformSettings> {

    private static final float  ARROW_WIDTH = 6f;
    private static final String ARROW_LEFT  = "←"; // ←
    private static final String ARROW_RIGHT = "→"; // →

    private BERLabel[] arrowLabels       = new BERLabel[0];
    private String     cachedStationFilter = "";

    @Override protected boolean showPlatform() { return false; }

    @Override
    protected float arrowReservedLeft(JREPlatformSettings settings) {
        return settings.showArrow() ? ARROW_WIDTH : 0f;
    }

    @Override
    protected float arrowReservedRight(JREPlatformSettings settings) {
        return settings.showArrow() ? ARROW_WIDTH : 0f;
    }

    // ── tick ──────────────────────────────────────────────────────────────
    @Override
    public void tick(Level level, BlockPos pos, BlockState state,
                     AdvancedDisplayBlockEntity be, AdvancedDisplayRenderInstance parent) {
        super.tick(level, pos, state, be, parent);
        Rectangle clip = Rectangle.withSize(2, 2,
            be.getXSizeScaled() * 16 - 4, be.getYSizeScaled() * 16 - 4);
        boolean glow = be.isGlowing();
        for (BERLabel lbl : arrowLabels) { lbl.clippingArea.set(clip); lbl.glowing.set(glow); }
    }

    // ── render ────────────────────────────────────────────────────────────
    @Override
    public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics,
                       float partial, AdvancedDisplayRenderInstance parent,
                       int light, boolean backSide) {
        super.render(graphics, partial, parent, light, backSide);

        JREPlatformSettings settings = getDisplaySettings(graphics.blockEntity());
        if (!settings.showArrow() || arrowLabels.length == 0) return;

        int totalW = graphics.blockEntity().getXSizeScaled() * 16;

        for (BERLabel lbl : arrowLabels) {
            String text = lbl.text.get().getString();
            if (text.isBlank()) continue;

            boolean isPhysicalLeft  = text.equals(ARROW_LEFT);
            boolean isPhysicalRight = text.equals(ARROW_RIGHT);

            // Position is always determined by which side the station is on.
            // x=3 = leftmost edge, x=totalW-3 = rightmost edge.
            // The back face rendering matrix is already mirrored (180deg Y rotation),
            // so x=3 appears on the viewer's left from the back face too.
            // We only need to flip the arrow SYMBOL, and the position.
            double y = lbl.position.get().y();
            //if (isPhysicalLeft)  lbl.position.set(Point.of(3f, y));
            //if (isPhysicalRight) lbl.position.set(Point.of(totalW - 3 - ARROW_WIDTH, y));

            // On back face, flip arrow symbol (← becomes →, → becomes ←)
            // because the station that was on the viewer's left from the front
            // is now on the viewer's right from the back.
            String symbol = text;
            //if (backSide) {
            //    if (isPhysicalLeft)  symbol = ARROW_RIGHT;
            //    if (isPhysicalRight) symbol = ARROW_LEFT;
            //}
            if (!backSide) {
                if (isPhysicalLeft)  lbl.position.set(Point.of(3f, y));
                if (isPhysicalRight) lbl.position.set(Point.of(totalW - 3 - ARROW_WIDTH, y));
            } else {
                // Swap position, keep symbol — so viewer always sees the correct arrow
                // pointing toward the correct side of the display
                if (isPhysicalLeft){
                    lbl.position.set(Point.of(totalW - 3 - ARROW_WIDTH, y));
                    symbol = ARROW_RIGHT;
                }
                if (isPhysicalRight){
                    lbl.position.set(Point.of(3f, y));
                    symbol = ARROW_LEFT;
                }
            }
            //lbl.render(graphics, light);
            lbl.text.set(de.mrjulsen.mcdragonlib.util.TextUtils.text(symbol));
            lbl.render(graphics, light);
            // Restore original symbol so update() result is preserved
            lbl.text.set(de.mrjulsen.mcdragonlib.util.TextUtils.text(text));
        }
    }

    // ── update ────────────────────────────────────────────────────────────
    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity be,
                       AdvancedDisplayRenderInstance parent, EUpdateReason reason) {
        // AbstractJRERenderer populates lastDepartures and lays out rows
        super.update(level, pos, state, be, parent, reason);

        JREPlatformSettings settings = getDisplaySettings(be);
        List<StationDisplayData> deps = lastDepartures;
        int count = deps.size();

        // Rebuild arrow labels if count changed
        if (arrowLabels.length != count) {
            arrowLabels = new BERLabel[count];
            for (int i = 0; i < count; i++) {
                BERLabel lbl = arrowLabels[i] = new BERLabel();
                lbl.verticalScale.set(Pair.of(BASE_SCALE, BASE_SCALE));
                lbl.horizontalScale.set(Pair.of(BASE_H_SCALE_MIN, BASE_SCALE));
                lbl.preferredWidth.set(ARROW_WIDTH);
            }
        }

        if (!settings.showArrow()) {
            for (BERLabel lbl : arrowLabels) lbl.text.set(TextUtils.empty());
            return;
        }

        for (int i = 0; i < count; i++) {
            StationDisplayData stop = deps.get(i);
            float y = 3 + i * LINE_HEIGHT;

            // Arrow direction: match each stop's station name / platform number
            // against the left/right filter strings the player configured.
            //
            // The player sets leftPlatform="T01", rightPlatform="T02" (exact or wildcard).
            // Each stop's scheduled station name comes from the timetable and is always
            // available regardless of how far the train is.
            //
            // Scheduled station is preferred (always populated from timetable).
            // Real-time is checked as fallback.
            String scTagName  = stop.getStationData().getScheduledStation().tagName();
            String scPlatform = stop.getStationData().getScheduledStation().info().platform();
            String rtTagName  = stop.getStationData().getRealTimeStation().tagName();
            String rtPlatform = stop.getStationData().getRealTimeStation().info().platform();

            String leftFilter  = settings.getLeftPlatform();
            String rightFilter = settings.getRightPlatform();

            // Check if this stop's station matches the left or right filter.
            // stationMatches(value, filter): value=stop's station name, filter=player input
            boolean matchLeft  = matchAny(leftFilter,  scTagName, scPlatform, rtTagName, rtPlatform);
            boolean matchRight = matchAny(rightFilter, scTagName, scPlatform, rtTagName, rtPlatform);

            String arrow = "";
            if (matchLeft) {
                arrow = ARROW_LEFT;
            } else if (matchRight) {
                arrow = ARROW_RIGHT;
            }

            arrowLabels[i].text.set(TextUtils.text(arrow));
            // x position is set in render() based on backSide flag
            arrowLabels[i].position.set(Point.of(3f, y));
            arrowLabels[i].color.set(settings.getFontColor());
        }
    }

    /**
     * Returns true if ANY of the candidate values matches the given filter.
     * The filter is what the player typed in the left/right platform box.
     * Candidates include: the block entity's station filter string, real-time
     * station name, scheduled station name, and platform numbers.
     * Uses CRN's stationMatches() which supports * wildcards.
     */
    private static boolean matchAny(String filter, String... candidates) {
        if (filter == null || filter.isBlank()) return false;
        for (String c : candidates) {
            if (c != null && !c.isBlank() && TrainUtils.stationMatches(c, filter)) return true;
        }
        return false;
    }
}
