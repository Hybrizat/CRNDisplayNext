package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.client.DynamicTextureHolder;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
import com.hybrizat.crndisplaynext.network.FetchImagePayload;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import de.mrjulsen.crn.client.ber.variants.AbstractAdvancedDisplayRenderer;
import de.mrjulsen.mcdragonlib.client.ber.BERGraphics;
import de.mrjulsen.mcdragonlib.client.util.RenderUtils;
import de.mrjulsen.mcdragonlib.util.DLColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BERJREGraphics implements AbstractAdvancedDisplayRenderer<GraphicsDisplaySettings> {

    private static final DLColor BG = DLColor.fromInt(0xFF1a1a2e);
    private static final Map<Long, DynamicTextureHolder> holders = new HashMap<>();
    private static final Map<Long, String> pendingUrls = new ConcurrentHashMap<>();
    private static Level lastLevel;

    private static final int P = 16;
    private static final float M = 2f;
    private static final int TEX = 256;

    private int cachedW = P, cachedH = P;

    @Override public void tick(Level level, BlockPos pos, BlockState state,
                                AdvancedDisplayBlockEntity be, AdvancedDisplayRenderInstance parent) {}

    @Override public void render(BERGraphics<AdvancedDisplayBlockEntity> graphics,
                                  float partial, AdvancedDisplayRenderInstance parent,
                                  int light, boolean backSide) {
        var be = graphics.blockEntity();
        if (!be.isController()) return;

        if (be.getLevel() != lastLevel) { closeAll(); lastLevel = be.getLevel(); }

        var facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var cfg = getDisplaySettings(be);
        String url = cfg.getImageUrl();
        long key = be.getBlockPos().asLong();

        RenderUtils.fillColor(graphics,
            new Vector3f(M, M, 0.01f), cachedW - 2*M, cachedH - 2*M, BG, facing);

        if (url.isBlank()) return;

        int texW = be.getXSizeScaled() * TEX;
        int texH = be.getYSizeScaled() * TEX;

        DynamicTextureHolder h = holders.get(key);
        if (h == null && texW > 0 && texH > 0) {
            h = new DynamicTextureHolder(texW, texH);
            holders.put(key, h);
        }
        if (h != null) {
            h.resize(texW, texH, url);
            // Request image from server cache (deduplicated via pendingUrls)
            if (!h.isReady() && pendingUrls.putIfAbsent(key, url) == null) {
                FetchImagePayload.request(be.getBlockPos(), url);
            }
            if (h.isReady()) {
                RenderUtils.renderTexture(
                    h.getId(), graphics,
                    new Vector3f(M, M, 0.02f),
                    cachedW - 2*M, cachedH - 2*M,
                    0, 0, 1, 1,
                    facing, DLColor.WHITE, false);
            }
        }
    }

    @Override public void update(Level level, BlockPos pos, BlockState state,
                                  AdvancedDisplayBlockEntity be,
                                  AdvancedDisplayRenderInstance parent, EUpdateReason reason) {
        if (!be.isController()) { cachedW = cachedH = 0; return; }
        cachedW = be.getXSizeScaled() * P;
        cachedH = be.getYSizeScaled() * P;
    }

    /** Called from ImageDataPayload handler on client. */
    public static void onImageData(BlockPos pos, byte[] data) {
        long key = pos.asLong();
        pendingUrls.remove(key);
        DynamicTextureHolder h = holders.get(key);
        if (h != null && data != null) {
            h.loadBytes(data);
        }
    }

    public static void release(long key) {
        var h = holders.remove(key);
        if (h != null) h.close();
    }

    private static void closeAll() {
        for (var h : holders.values()) h.close();
        holders.clear();
        pendingUrls.clear();
    }
}
