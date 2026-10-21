package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.compat.DisplayEntry;
import com.hybrizat.crndisplaynext.display.settings.JREPlatformSettings;
import de.mrjulsen.crn.api.core.ref.StationRef;
import de.mrjulsen.crn.api.core.snapshot.BoardEntry;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.util.TrainUtils;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel;
import de.mrjulsen.mcdragonlib.client.ber.BERLabel.EScrollMode;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
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
 *   columns never overlap the arrow columns. Space is only reserved for a side
 *   that actually has a filter configured, so enabling the feature without any
 *   filter does not shift the board.
 *
 *   Both faces get exactly the same arrow, just like the text rows: CRN mirrors
 *   the whole display for the back face, so local x = 3 is on the viewer's left
 *   from either side.
 *
 * Matching (per row):
 *   The platform at which this train calls at this display's station decides the
 *   side. CRN has no notion of a physical left/right platform, so the player
 *   names the platforms: leftPlatform / rightPlatform are compared against the
 *   call's own station and platform using TrainUtils.stationMatches(), which
 *   supports * wildcards.
 *
 *   Candidates are the station's own name, its station-tag name, its platform
 *   ("1"), and its platform as the board prints it ("1番線") — the platform
 *   column of the station display uses the same value plus the localized
 *   suffix, so a player can copy either form.
 *
 *   A row that matches both filters is shown as ← (left wins). Both filters
 *   empty means no arrows at all.
 */
public class BERJREPlatform extends AbstractJRERenderer<JREPlatformSettings> {

    private static final float  ARROW_WIDTH = 6f;
    private static final String ARROW_LEFT  = "←"; // ←
    private static final String ARROW_RIGHT = "→"; // →

    /** No arrow for this row. */
    private static final byte SIDE_NONE  =  0;
    /** This row calls at a platform configured as the left one. */
    private static final byte SIDE_LEFT  = -1;
    /** This row calls at a platform configured as the right one. */
    private static final byte SIDE_RIGHT =  1;

    private BERLabel[] arrowLabels = new BERLabel[0];
    /** One entry per rendered row, resolved in update() so render() stays trivial. */
    private byte[]     arrowSides  = new byte[0];

    @Override protected boolean showPlatform() { return false; }

    @Override
    protected float arrowReservedLeft(JREPlatformSettings settings) {
        return hasFilter(settings.showArrow(), settings.getLeftPlatform()) ? ARROW_WIDTH : 0f;
    }

    @Override
    protected float arrowReservedRight(JREPlatformSettings settings) {
        return hasFilter(settings.showArrow(), settings.getRightPlatform()) ? ARROW_WIDTH : 0f;
    }

    private static boolean hasFilter(boolean enabled, String filter) {
        return enabled && filter != null && !filter.isBlank();
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

        int count = Math.min(arrowSides.length, arrowLabels.length);
        if (count == 0) return;

        int totalW = graphics.blockEntity().getXSizeScaled() * 16;

        for (int i = 0; i < count; i++) {
            byte side = arrowSides[i];
            if (side == SIDE_NONE) continue;

            boolean left = side == SIDE_LEFT;
            BERLabel lbl = arrowLabels[i];
            lbl.text.set(TextUtils.text(left ? ARROW_LEFT : ARROW_RIGHT));
            lbl.position.set(Point.of(left ? 3f : totalW - 3 - ARROW_WIDTH, 3f + i * LINE_HEIGHT));
            lbl.render(graphics, light);
        }
    }

    // ── update ────────────────────────────────────────────────────────────
    @Override
    public void update(Level level, BlockPos pos, BlockState state,
                       AdvancedDisplayBlockEntity be,
                       AdvancedDisplayRenderInstance parent, EUpdateReason reason) {
        // AbstractJRERenderer populates lastDepartures and lays out rows
        super.update(level, pos, state, be, parent, reason);

        if (!be.isController()) {
            arrowLabels = new BERLabel[0];
            arrowSides  = new byte[0];
            return;
        }

        JREPlatformSettings settings = getDisplaySettings(be);
        List<DisplayEntry> deps = lastDepartures;
        int count = deps.size();

        if (arrowLabels.length != count) {
            arrowLabels = new BERLabel[count];
            for (int i = 0; i < count; i++) {
                BERLabel lbl = arrowLabels[i] = new BERLabel();
                lbl.verticalScale.set(Pair.of(BASE_SCALE, BASE_SCALE));
                lbl.horizontalScale.set(Pair.of(BASE_H_SCALE_MIN, BASE_SCALE));
                lbl.preferredWidth.set(ARROW_WIDTH);
                lbl.horizontalAlign.set(ETextAlignment.CENTER);
                // A single glyph must never scroll inside its 6px slot.
                lbl.horizontalScrollMode.set(EScrollMode.NEVER);
            }
        }

        arrowSides = new byte[count];
        if (!settings.showArrow()) return;

        String leftFilter  = settings.getLeftPlatform();
        String rightFilter = settings.getRightPlatform();
        String platformSuffix = loc("gui.crndisplaynext.jre.platform_suffix", "番線");

        for (int i = 0; i < count; i++) {
            BoardEntry raw = deps.get(i).getStationData().raw();
            if (raw == null) continue;

            boolean matchLeft  = matchesAny(leftFilter,  raw, platformSuffix);
            boolean matchRight = matchesAny(rightFilter, raw, platformSuffix);

            byte side = SIDE_NONE;
            if (matchLeft) {
                side = SIDE_LEFT;        // left wins when both filters match
            } else if (matchRight) {
                side = SIDE_RIGHT;
            }
            arrowSides[i] = side;
            arrowLabels[i].color.set(settings.getFontColor());
        }

        logArrows(be, leftFilter, rightFilter, deps);
    }

    /**
     * True when any candidate value of the call's station matches the filter.
     * Both the timetabled station and the real-time one are considered: a
     * diverted train still has to get the arrow of the platform it really uses.
     */
    private static boolean matchesAny(String filter, BoardEntry raw, String platformSuffix) {
        if (filter == null || filter.isBlank()) return false;
        return matches(filter, raw.station(), platformSuffix)
            || matches(filter, raw.scheduledStation(), platformSuffix);
    }

    private static boolean matches(String filter, StationRef station, String platformSuffix) {
        if (station == null || !station.isKnown()) return false;
        String platform = station.platform();
        return matchesCandidate(station.name(), filter)
            || matchesCandidate(station.tagName(), filter)
            || matchesCandidate(platform, filter)
            || matchesCandidate(platform == null || platform.isBlank() ? "" : platform + platformSuffix, filter);
    }

    private static boolean matchesCandidate(String candidate, String filter) {
        if (candidate == null || candidate.isBlank()) return false;
        return TrainUtils.stationMatches(candidate.trim(), filter.trim());
    }

    /** Diagnostic only: lists what each row could have matched, at DEBUG level. */
    private void logArrows(AdvancedDisplayBlockEntity be, String leftFilter,
                           String rightFilter, List<DisplayEntry> deps) {
        if (!CRNDisplayNextMod.LOGGER.isDebugEnabled()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < deps.size(); i++) {
            StationRef st = deps.get(i).getStationData().raw().station();
            if (i > 0) sb.append(", ");
            sb.append('[').append(st.name()).append('/').append(st.tagName())
              .append('/').append(st.platform()).append('=')
              .append(arrowSides[i] == SIDE_LEFT ? "L" : arrowSides[i] == SIDE_RIGHT ? "R" : "-")
              .append(']');
        }
        CRNDisplayNextMod.LOGGER.debug("[Arrow] {} L='{}' R='{}' rows={}: {}",
            be.getBlockPos(), leftFilter, rightFilter, deps.size(), sb);
    }
}
