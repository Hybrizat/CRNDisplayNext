package com.hybrizat.crndisplaynext.block.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import static com.hybrizat.crndisplaynext.registry.ModBlockEntities.GRAPHICS_DISPLAY_BE;

public class GraphicsDisplayBlockEntity extends BlockEntity {

    private static final String K_CX="cx", K_CY="cy", K_CZ="cz", K_CTRL="ctrl",
        K_W="w", K_H="h", K_GX="gx", K_GY="gy", K_BG="bg";

    private BlockPos controllerPos;
    private boolean  isController  = true;
    private int      displayWidth  = 1;
    private int      displayHeight = 1;
    private int      gridX = 0, gridY = 0;
    private int      bgColor = 0xFF1a1a2e;

    public GraphicsDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(GRAPHICS_DISPLAY_BE.get(), pos, state);
        this.controllerPos = pos;
    }

    public boolean  isController()    { return isController; }
    public BlockPos getControllerPos() { return controllerPos; }
    public int      getDisplayWidth()  { return displayWidth; }
    public int      getDisplayHeight() { return displayHeight; }
    public int      getGridX()         { return gridX; }
    public int      getGridY()         { return gridY; }
    public int      getBgColor()       { return bgColor; }
    public void     setBgColor(int c)  { bgColor = c; setChanged(); syncToClients(); }

    public boolean expand(int w, int h) {
        if (!isController || level == null) return false;
        var f = getBlockState().getValue(
            com.hybrizat.crndisplaynext.block.GraphicsDisplayBlock.FACING);
        var right = f.getClockWise();
        for (int dy=0; dy<h; dy++) for (int dx=0; dx<w; dx++) {
            var cell = worldPosition.relative(right, dx).relative(Direction.DOWN, dy);
            if (!(level.getBlockEntity(cell) instanceof GraphicsDisplayBlockEntity))
                return false;
        }
        shrinkToOne(level);
        for (int dy=0; dy<h; dy++) for (int dx=0; dx<w; dx++) {
            var cell = worldPosition.relative(right, dx).relative(Direction.DOWN, dy);
            if (level.getBlockEntity(cell) instanceof GraphicsDisplayBlockEntity m) {
                m.isController = (dx==0&&dy==0);
                m.controllerPos = worldPosition;
                m.displayWidth = w; m.displayHeight = h;
                m.gridX = dx; m.gridY = dy;
                m.bgColor = bgColor;
                m.setChanged(); m.syncToClients();
            }
        }
        displayWidth = w; displayHeight = h;
        setChanged(); syncToClients();
        return true;
    }

    public void shrinkToOne(Level level) {
        if (!isController || level == null) return;
        var f = getBlockState().getValue(
            com.hybrizat.crndisplaynext.block.GraphicsDisplayBlock.FACING);
        var right = f.getClockWise();
        for (int dy=0; dy<displayHeight; dy++) for (int dx=0; dx<displayWidth; dx++) {
            if (dx==0&&dy==0) continue;
            var cell = worldPosition.relative(right, dx).relative(Direction.DOWN, dy);
            if (level.getBlockEntity(cell) instanceof GraphicsDisplayBlockEntity m) {
                m.isController = true;
                m.controllerPos = m.getBlockPos();
                m.displayWidth = 1; m.displayHeight = 1;
                m.gridX = 0; m.gridY = 0;
                m.setChanged(); m.syncToClients();
            }
        }
        displayWidth = 1; displayHeight = 1;
    }

    public void onBreak(Level level) {
        if (level == null) return;
        if (isController) shrinkToOne(level);
        else if (level.getBlockEntity(controllerPos) instanceof GraphicsDisplayBlockEntity c)
            c.shrinkToOne(level);
    }

    public void openGuiClient() {
        Minecraft.getInstance().setScreen(
            new com.hybrizat.crndisplaynext.client.screen.GraphicsDisplayScreen(this));
    }

    @Override protected void saveAdditional(CompoundTag t, HolderLookup.Provider regs) {
        super.saveAdditional(t, regs);
        t.putInt(K_CX,controllerPos.getX()); t.putInt(K_CY,controllerPos.getY());
        t.putInt(K_CZ,controllerPos.getZ()); t.putBoolean(K_CTRL,isController);
        t.putInt(K_W,displayWidth); t.putInt(K_H,displayHeight);
        t.putInt(K_GX,gridX); t.putInt(K_GY,gridY); t.putInt(K_BG,bgColor);
    }

    @Override protected void loadAdditional(CompoundTag t, HolderLookup.Provider regs) {
        super.loadAdditional(t, regs);
        controllerPos=new BlockPos(t.getInt(K_CX),t.getInt(K_CY),t.getInt(K_CZ));
        isController=t.getBoolean(K_CTRL);
        displayWidth=Math.max(1,t.getInt(K_W)); displayHeight=Math.max(1,t.getInt(K_H));
        gridX=t.getInt(K_GX); gridY=t.getInt(K_GY); bgColor=t.getInt(K_BG);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider regs) { var t=new CompoundTag(); saveAdditional(t, regs); return t; }

    @Nullable @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void syncToClients() {
        if (level instanceof ServerLevel sl)
            sl.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }
}
