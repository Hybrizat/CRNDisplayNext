package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.api.IAdvancedDisplayBEExt;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client→Server: toggle technical-stop hiding on a display (Forge 47 style). */
public record HideTechnicalStopsPacket(BlockPos pos, boolean hide) {

    public static HideTechnicalStopsPacket read(FriendlyByteBuf buf) {
        return new HideTechnicalStopsPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void toBytes(HideTechnicalStopsPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos());
        buf.writeBoolean(pkt.hide());
    }

    public static void handle(HideTechnicalStopsPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ServerPlayer player = ctxSupplier.get().getSender();
        if (player == null) return;
        ctxSupplier.get().enqueueWork(() -> {
            ServerLevel level = player.serverLevel();

            if (player.blockPosition().distSqr(pkt.pos()) > 64 * 64) {
                CRNDisplayNextMod.LOGGER.warn("[CRNExt] Player {} tried to modify display from too far away", player.getName().getString());
                return;
            }

            BlockEntity be = level.getBlockEntity(pkt.pos());
            if (be == null) return;

            if (!(be instanceof IAdvancedDisplayBEExt ext)) {
                CRNDisplayNextMod.LOGGER.warn("[CRNExt] Block entity at {} does not implement IAdvancedDisplayBEExt", pkt.pos());
                return;
            }

            ext.crndisplaynext$setHideTechnicalStops(pkt.hide());
            be.setChanged();
            level.sendBlockUpdated(pkt.pos(), be.getBlockState(), be.getBlockState(), 3);

            CRNDisplayNextMod.LOGGER.debug("[CRNExt] Set hideTechnicalStops={} at {}", pkt.hide(), pkt.pos());
        });
    }

    public static void sendToServer(BlockPos pos, boolean hide) {
        NetworkManager.CHANNEL.sendToServer(new HideTechnicalStopsPacket(pos, hide));
    }
}