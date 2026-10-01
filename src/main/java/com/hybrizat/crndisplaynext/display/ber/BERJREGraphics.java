package com.hybrizat.crndisplaynext.display.ber;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.client.DynamicTextureHolder;
import com.hybrizat.crndisplaynext.client.TextureHolderSweeper;
import com.hybrizat.crndisplaynext.client.ImageReassembler;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
import com.hybrizat.crndisplaynext.network.FetchImagePayload;
import com.hybrizat.crndisplaynext.util.ImageUrlPolicy;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renderer for the jre_graphics display type.
 *
 * <p>The client never downloads images itself: it requests from the server
 * (FetchImagePayload) and receives chunked image data (ImageDataPayload).
 * If the server does not deliver within the timeout window, the URL is marked
 * failed instead of falling back to a client-side download, so the server
 * stays the single download authority (cache + URL policy).</p>
 */
public class BERJREGraphics implements AbstractAdvancedDisplayRenderer<GraphicsDisplaySettings> {

    private static final DLColor BG = DLColor.fromInt(0xFF1a1a2e);
    /**
     * A holder plus the last client tick in which its display was confirmed alive
     * (CRN called tick/render/update for this exact block entity instance).
     */
    private static final class Entry {
        /** Block entity this holder is bound to. Re-bound (not replaced) when
         *  the BE is recreated by a chunk reload, so the texture survives. */
        AdvancedDisplayBlockEntity be;
        final DynamicTextureHolder holder;
        /** URL currently requested/loaded for this holder. */
        volatile String url;
        int lastActive;

        Entry(AdvancedDisplayBlockEntity be, DynamicTextureHolder holder, String url) {
            this.be = be;
            this.holder = holder;
            this.url = url;
            this.lastActive = TextureHolderSweeper.clientTicks;
        }

        void touch() { lastActive = TextureHolderSweeper.clientTicks; }
    }
    /** A display is considered dead after this many ticks without renderer activity. */
    static final int STALE_AFTER_TICKS = 200;
    private static final Map<Long, Entry> holders = new ConcurrentHashMap<>();
    /** key → request start time (ms). Pending server fetch requests. */
    private static final Map<Long, Long> pendingUrls = new ConcurrentHashMap<>();
    /** key → URL the server failed to deliver; do not re-request until it changes. */
    private static final Map<Long, String> failedUrls = new ConcurrentHashMap<>();
    /** If the server hasn't replied within this window, stop retrying (and log). */
    private static final long FETCH_TIMEOUT_MS = 5000L;

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

        // NOTE: there used to be a `if (be.getLevel() != lastLevel) closeAll();` guard
        // here. closeAll() wipes holders AND pendingUrls, and be.getLevel() is not
        // stable for contraption-mounted displays, so the guard fired continuously and
        // destroyed the in-flight marker on every frame. putIfAbsent then always
        // returned null, so every frame re-sent the fetch and the request never
        // completed — a 4600-request-per-10s storm with no image ever arriving.
        // Reclamation is already handled properly by the activity-based cleanup().

        var facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var cfg = getDisplaySettings(be);
        String url = cfg.getImageUrl();
        long key = be.getBlockPos().asLong();

        RenderUtils.fillColor(graphics,
            new Vector3f(M, M, 0.01f), cachedW - 2*M, cachedH - 2*M, BG, facing);

        if (url.isBlank()) { logThrottled("[Gfx] render: url blank"); return; }
        if (CRNDisplayNextMod.LOGGER.isDebugEnabled()) CRNDisplayNextMod.LOGGER.debug("[Gfx] render: url='{}'", url);

        int texW = be.getXSizeScaled() * TEX;
        int texH = be.getYSizeScaled() * TEX;

        Entry entry = holders.get(key);
        if (entry != null && entry.be != be) {
            // Block entity was recreated (chunk unload/reload): keep the
            // existing texture and just re-bind it. Releasing here forced a
            // full re-download (uplink spike) and a blank frame (flicker)
            // every time the player crossed the chunk-load boundary.
            if (entry.url != null && !entry.url.equals(url)) {
                release(key); // configured image actually changed → start fresh
            } else {
                entry.be = be;
            }
        }
        entry = holders.get(key);
        if (entry == null && texW > 0 && texH > 0) {
            entry = new Entry(be, new DynamicTextureHolder(texW, texH), null);
            holders.put(key, entry);
        }
        if (entry != null) entry.touch();
        DynamicTextureHolder h = entry != null ? entry.holder : null;
        if (h != null) {
            h.resize(texW, texH);
            if (entry.url != null && !entry.url.equals(url)) {
                // Configured image changed while the holder was alive → drop
                // the old request state and fetch the new one (the old image
                // stays visible until the new upload completes).
                pendingUrls.remove(key);
                failedUrls.remove(key);
                ImageReassembler.drop(key);
                entry.url = null;
            }
            if (!h.isReady()) {
                if (!ImageUrlPolicy.isPlausibleImageUrl(url)) {
                    logThrottled("[Gfx] not a valid image URL (need http(s) with host): " + url);
                } else if (url.equals(failedUrls.get(key))) {
                    // Already failed for this URL; wait for the user to change it.
                } else {
                    Long started = pendingUrls.putIfAbsent(key, System.currentTimeMillis());
                    if (started == null) {
                        // First request → ask the server (cache or download).
                        entry.url = url;
                        CRNDisplayNextMod.LOGGER.info("[Gfx] requesting from server: {}", url);
                        FetchImagePayload.request(be.getBlockPos(), url);
                    } else if (System.currentTimeMillis() - started > FETCH_TIMEOUT_MS) {
                        // Server never replied (download failed or URL rejected).
                        pendingUrls.remove(key);
                        failedUrls.put(key, url);
                        CRNDisplayNextMod.LOGGER.warn("[Gfx] server did not deliver image for {}; marked failed until URL changes", url);
                    }
                }
            }
            if (h.isReady()) {
                pendingUrls.remove(key); // transfer finished → clear in-flight marker
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
                                AdvancedDisplayBlockEntity be, AdvancedDisplayRenderInstance parent, EUpdateReason reason) {
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

    /** Called from ImageDataPayload handler (client) once a full image arrived. */
    public static void onImageData(BlockPos pos, byte[] data) {
        long key = pos.asLong();
        Entry e = holders.get(key);
        if (e == null || data == null) {
            pendingUrls.remove(key); // delivered, but the holder is gone — drop the marker
            return;
        }
        // Keep the in-flight marker until the holder is ACTUALLY ready
        // (decode + GL upload complete). Removing it early let every render
        // frame during the decode gap re-request the whole image → request
        // storm + uplink saturation + visible flicker.
        e.holder.loadBytes(data,
            () -> pendingUrls.remove(key), // success → ready to render
            () -> {                        // undecodable → stop retrying this URL
                pendingUrls.remove(key);
                if (e.url != null) failedUrls.put(key, e.url);
            });
    }

    public static void release(long key) {
        var e = holders.remove(key);
        pendingUrls.remove(key);
        failedUrls.remove(key);
        ImageReassembler.drop(key);
        if (e != null) e.holder.close();
    }

    /**
     * Activity-based reclamation: release a holder only after its display has
     * been quiet for STALE_AFTER_TICKS (block unloaded, removed, or the
     * carriage dismantled). Live displays keep getting touched from render();
     * a re-created display re-requests its image from the server on the next
     * render pass.
     */
    public static void cleanup() {
        int now = TextureHolderSweeper.clientTicks;
        for (long key : new ArrayList<>(holders.keySet())) {
            Entry e = holders.get(key);
            if (e != null && now - e.lastActive >= STALE_AFTER_TICKS) release(key);
        }
    }
}
