package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.api.IAdvancedDisplayBEExt;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HideTechnicalStopsPacket(BlockPos pos, boolean hide) implements CustomPacketPayload {

    // NeoForge 1.21.1: use ResourceLocation.fromNamespaceAndPath() instead of new ResourceLocation(ns, path)
    public static final CustomPacketPayload.Type<HideTechnicalStopsPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "hide_technical_stops")
        );

    public static final StreamCodec<FriendlyByteBuf, HideTechnicalStopsPacket> STREAM_CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos());
                buf.writeBoolean(pkt.hide());
            },
            buf -> new HideTechnicalStopsPacket(buf.readBlockPos(), buf.readBoolean())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // NeoForge 1.21.1: PacketDistributor.sendToServer() replaces SERVER.noArg().send()
    public static void sendToServer(BlockPos pos, boolean hide) {
        PacketDistributor.sendToServer(new HideTechnicalStopsPacket(pos, hide));
    }

    public static void handle(HideTechnicalStopsPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
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
}
