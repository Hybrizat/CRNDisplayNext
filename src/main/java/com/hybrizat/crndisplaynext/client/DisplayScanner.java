package com.hybrizat.crndisplaynext.client;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.mojang.brigadier.CommandDispatcher;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * TEMPORARY diagnostic: {@code /crndscan}.
 *
 * <p>Dumps every display block entity the client can see, so a healthy multi-block
 * display can be compared against a broken one under an identical layout. A correct
 * multi-block display should show exactly ONE controller per display carrying the
 * FULL size, with the remaining blocks reporting {@code isController() == false}.</p>
 */
@EventBusSubscriber(modid = CRNDisplayNextMod.MOD_ID)
public final class DisplayScanner {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("crndscan").executes(DisplayScanner::run));
    }

    private static int run(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var lv = src.getLevel();
        List<AdvancedDisplayBlockEntity> found = new ArrayList<>();
        int total = 0;
        try {
            var f = findBlockEntitiesField(lv.getClass());
            if (f == null) {
                send(src, "cannot locate block entity map on " + lv.getClass().getSimpleName());
                return 0;
            }
            f.setAccessible(true);
            Object mapObj = f.get(lv);
            if (mapObj instanceof java.util.Map<?, ?> m) {
                for (Object v : m.values()) {
                    total++;
                    if (v instanceof AdvancedDisplayBlockEntity d) found.add(d);
                }
            }
        } catch (Throwable t) {
            send(src, "iterate failed: " + t);
            return 0;
        }

        found.sort(Comparator
            .comparingInt((AdvancedDisplayBlockEntity d) -> d.getBlockPos().getZ())
            .thenComparingInt(d -> d.getBlockPos().getY())
            .thenComparingInt(d -> d.getBlockPos().getX()));

        send(src, "scanned " + total + " block entities, " + found.size() + " displays");
        for (AdvancedDisplayBlockEntity d : found) {
            BlockPos p = d.getBlockPos();
            String lvName = d.getLevel() == null ? "null" : d.getLevel().getClass().getSimpleName();
            String line = String.format(Locale.ROOT,
                "pos=%s ctrl=%s size=%dx%d scaled=%dx%d type=%s lv=%s facing=%s",
                p, d.isController(), d.getXSize(), d.getYSize(),
                d.getXSizeScaled(), d.getYSizeScaled(),
                d.getDisplayType(), lvName,
                d.getBlockState().getValue(HorizontalDirectionalBlock.FACING));
            CRNDisplayNextMod.LOGGER.info("[Scan] {}", line);
            send(src, line);
        }
        return found.size();
    }

    private static void send(CommandSourceStack src, String msg) {
        CRNDisplayNextMod.LOGGER.info("[Scan] {}", msg);
        src.sendSuccess(() -> Component.literal("[Scan] " + msg), false);
    }

    /** Walks the class hierarchy looking for the Map-typed block entity store. */
    private static java.lang.reflect.Field findBlockEntitiesField(Class<?> start) {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            for (var f : c.getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (n.contains("blockentit") || n.contains("block_ent")) return f;
                }
            }
        }
        return null;
    }
}