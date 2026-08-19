package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;

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
import net.minecraft.client.Minecraft;
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
    private record Entry(AdvancedDisplayBlockEntity be, DynamicTextureHolder holder) {}
    private static final Map<Long, Entry> holders = new HashMap<>();
    /** key → request start time (ms). Pending server fetch requests. */
    private static final Map<Long, Long> pendingUrls = new ConcurrentHashMap<>();
    /** If the server hasn't replied within this window, fall back to client-side download. */
    private static final long FETCH_TIMEOUT_MS = 5000L;
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
        if (!be.isController()) { logThrottled("[Gfx] render: not controller"); return; }

        if (be.getLevel() != lastLevel) { closeAll(); lastLevel = be.getLevel(); }

        var facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var cfg = getDisplaySettings(be);
        String url = cfg.getImageUrl();
        long key = be.getBlockPos().asLong();

        RenderUtils.fillColor(graphics,
            new Vector3f(M, M, 0.01f), cachedW - 2*M, cachedH - 2*M, BG, facing);

        if (url.isBlank()) { logThrottled("[Gfx] render: url blank"); return; }
        CRNDisplayNextMod.LOGGER.info("[Gfx] render: url='{}'", url);

        int texW = be.getXSizeScaled() * TEX;
        int texH = be.getYSizeScaled() * TEX;

        Entry entry = holders.get(key);
        if (entry != null && entry.be() != be) release(key);
        entry = holders.get(key);
        if (entry == null && texW > 0 && texH > 0) {
            entry = new Entry(be, new DynamicTextureHolder(texW, texH));
            holders.put(key, entry);
        }
        DynamicTextureHolder h = entry != null ? entry.holder() : null;
        if (h != null) {
            h.resize(texW, texH, url);
            if (!h.isReady()) {
                Long started = pendingUrls.putIfAbsent(key, System.currentTimeMillis());
                if (started == null) {
                    // First request → ask the server cache
                    CRNDisplayNextMod.LOGGER.info("[Gfx] requesting from server: {}", url);
                    FetchImagePayload.request(be.getBlockPos(), url);
                } else if (System.currentTimeMillis() - started > FETCH_TIMEOUT_MS) {
                    // Server never replied (dedicated-server download failed).
                    // Fall back to client-side direct download.
                    CRNDisplayNextMod.LOGGER.info("[Gfx] server timeout, direct download: {}", url);
                    pendingUrls.remove(key);
                    h.loadUrl(url);
                }
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

    /** Throttle repetitive render diagnostics to at most once per second. */
    private static long lastLogTime = 0;
    private static void logThrottled(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= 1000L) {
            lastLogTime = now;
            CRNDisplayNextMod.LOGGER.info(msg);
        }
    }

    /** Called from ImageDataPayload handler on client. */
    public static void onImageData(BlockPos pos, byte[] data) {
        long key = pos.asLong();
        pendingUrls.remove(key);
        Entry e = holders.get(key);
        if (e != null && data != null) {
            e.holder().loadBytes(data);
        }
    }

    public static void release(long key) {
        var e = holders.remove(key);
        pendingUrls.remove(key);
        if (e != null) e.holder().close();
    }

    /** Release holders whose block entity is gone or no longer a graphics display. */
    public static void sweep() {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (long key : new ArrayList<>(holders.keySet())) {
            Entry e = holders.get(key);
            if (e == null) continue;
            var be = e.be();
            if (level.getBlockEntity(be.getBlockPos()) != be
                    || !be.isController()
                    || !(be.getSettings() instanceof GraphicsDisplaySettings)) {
                release(key);
            }
        }
    }

    private static void closeAll() {
        for (var e : holders.values()) e.holder().close();
        holders.clear();
        pendingUrls.clear();
    }
}
