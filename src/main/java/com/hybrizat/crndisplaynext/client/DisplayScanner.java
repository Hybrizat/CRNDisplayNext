package com.hybrizat.crndisplaynext.client;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TEMPORARY diagnostic.
 *
 * <p>Dumps every display block entity the client can see — position, controller
 * flag, declared size, display type and level class — so a healthy CRN-native
 * display can be compared against ours under the identical layout. Answer to the
 * open question: a correct multi-block display should show exactly ONE controller
 * per display carrying the FULL size, with every other block reporting
 * {@code isController() == false}. Two controllers of half size means the merge
 * produced two separate displays instead of one.</p>
 */
public final class DisplayScanner {

    private static int countdown = 100; // fire shortly after joining the world

    /** Walks the class hierarchy looking for the Map-typed block entity store. */
    private static java.lang.reflect.Field findBlockEntitiesField(Class<?> start) {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            for (var f : c.getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        if (f.getName().toLowerCase(java.util.Locale.ROOT).contains("blockentit")
                                || f.getName().toLowerCase(java.util.Locale.ROOT).contains("block_ent")) {
                            return f;
                        }
                    } catch (Throwable ignored) { }
                }
            }
        }
        return null;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (--countdown > 0) return;
        countdown = 200; // every 10 seconds

        Level lv = event.getLevel();
        if (lv == null || !lv.isClientSide()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lv) return;

        List<AdvancedDisplayBlockEntity> found = new ArrayList<>();
        try {
            // Level's block-entity map field: name differs between mappings, so probe
            // reflectively instead of binding to one.
            try {
                var f = findBlockEntitiesField(lv.getClass());
                if (f == null) {
                    CRNDisplayNextMod.LOGGER.info("[Scan] cannot locate blockEntities field on {}", lv.getClass());
                    return;
                }
                f.setAccessible(true);
                Object mapObj = f.get(lv);
                int n = 0;
                if (mapObj instanceof java.util.Map<?, ?> m) {
                    for (Object v : m.values()) {
                        n++;
                        if (v instanceof AdvancedDisplayBlockEntity d) found.add(d);
                    }
                }
                CRNDisplayNextMod.LOGGER.info("[Scan] iterated {} block entities, {} displays", n, found.size());
            } catch (Throwable t) {
                CRNDisplayNextMod.LOGGER.info("[Scan] iterate failed: {}", t.toString());
                return;
            }
        } catch (Throwable t) {
            CRNDisplayNextMod.LOGGER.info("[Scan] iterate failed: {}", t.toString());
            return;
        }
        if (found.isEmpty()) return;

        found.sort(Comparator
            .comparingInt((AdvancedDisplayBlockEntity d) -> d.getBlockPos().getZ())
            .thenComparingInt(d -> d.getBlockPos().getY())
            .thenComparingInt(d -> d.getBlockPos().getX()));
        CRNDisplayNextMod.LOGGER.info("[Scan] {} display block entities visible on client", found.size());
        for (AdvancedDisplayBlockEntity d : found) {
            BlockPos p = d.getBlockPos();
            String type = String.valueOf(d.getDisplayType());
            String lvName = d.getLevel() == null ? "null" : d.getLevel().getClass().getSimpleName();
            CRNDisplayNextMod.LOGGER.info(
                "[Scan] pos={} ctrl={} size={}x{} scaled={}x{} type={} lv={} sameLevel={} facing={}",
                p, d.isController(), d.getXSize(), d.getYSize(),
                d.getXSizeScaled(), d.getYSizeScaled(), type, lvName,
                d.getLevel() == lv,
                d.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
        }
    }
}