package com.hybrizat.crndisplaynext.client.screen;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.client.ImageExecutors;
import com.hybrizat.crndisplaynext.network.FetchImagePayload;
import com.hybrizat.crndisplaynext.network.GraphicsUrlPayload;
import com.hybrizat.crndisplaynext.network.RequestCachePayload;
import com.hybrizat.crndisplaynext.util.ImageUrlPolicy;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Image settings GUI.
 *
 * <p>All image I/O is server-driven: "Load" asks the server to fetch the URL
 * (cache or download) and displays the bytes the server sends back, so the
 * client never opens its own HTTP connections.</p>
 */
public class GraphicsImageScreen extends Screen {

    private static final ResourceLocation PREVIEW_TEX =
        ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "gfx_preview");
    private static final long PREVIEW_TIMEOUT_MS = 10_000L;

    /** Called by CacheListPayload handler to deliver the cache listing to the open screen. */
    public static void onCacheData(List<String> rawEntries) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof GraphicsImageScreen gs) {
            gs.setCacheData(rawEntries);
            CRNDisplayNextMod.LOGGER.info("[GUI] Injected {} cache entries into open screen", rawEntries.size());
        }
    }

    /** Called by ImageDataPayload handler when the server delivers an image for this display. */
    public static void onImageData(BlockPos pos, byte[] data) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof GraphicsImageScreen gs && gs.bePos.equals(pos)) {
            gs.showPreview(data);
        }
    }

    private final BlockPos bePos;
    private String urlDraft;
    private EditBox urlInput;
    private ResourceLocation previewId;
    private int previewW, previewH;
    private Component status;
    private boolean tabCache;
    private long previewRequestedAt;

    // Cached entries with textures (instance-local, avoids classloader issues)
    private record Cached(String id, String url, int w, int h, ResourceLocation tex) {}
    private final List<Cached> cachedEntries = new ArrayList<>();
    private final Map<String, ResourceLocation> thumbs = new HashMap<>();
    private int texIdx;

    public GraphicsImageScreen(BlockPos bePos, String currentUrl) {
        super(Component.literal("Image Settings"));
        this.bePos = bePos;
        this.urlDraft = currentUrl == null ? "" : currentUrl;
    }

    void setCacheData(List<String> raw) {
        for (Cached c : cachedEntries) if (c.tex != null) Minecraft.getInstance().getTextureManager().release(c.tex);
        cachedEntries.clear();
        for (String s : raw) {
            String[] p = s.split("\t", -1);
            if (p.length < 5) continue;
            ResourceLocation tex = null;
            if (!p[4].isEmpty()) try {
                byte[] b = Base64.getDecoder().decode(p[4]);
                NativeImage ni = NativeImage.read(new java.io.ByteArrayInputStream(b));
                var dt = new DynamicTexture(ni);
                tex = ResourceLocation.fromNamespaceAndPath("crndisplaynext", "thumb/" + (texIdx++));
                Minecraft.getInstance().getTextureManager().register(tex, dt);
            } catch (Exception e) {}
            cachedEntries.add(new Cached(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), tex));
        }
    }

    @Override protected void init() {
        int cx = width / 2, top = 25;
        addRenderableWidget(Button.builder(Component.literal("URL"), b -> { tabCache = false; rebuild(); })
            .pos(cx - 152, top).size(75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cached"), b -> {
            tabCache = true; RequestCachePayload.request(); rebuild();
        }).pos(cx - 77, top).size(75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
            .pos(cx - 100, height - 35).size(200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .pos(cx - 30, height - 58).size(60, 20).build());
        if (!tabCache) {
            urlInput = new EditBox(font, cx - 155, top + 28, 310, 20, Component.literal("URL"));
            urlInput.setMaxLength(2048);
            urlInput.setValue(urlDraft);
            addRenderableWidget(urlInput);
            addRenderableWidget(Button.builder(Component.literal("Load"), b -> requestPreview())
                .pos(cx + 160, top + 28).size(50, 20).build());
        }
    }

    void rebuild() { clearWidgets(); init(); }

    private void apply() {
        if (!tabCache && urlInput != null) {
            String u = urlInput.getValue().trim();
            if (u.isBlank()) return;
            if (!ImageUrlPolicy.isPlausibleImageUrl(u)) {
                status = Component.literal("Not an image URL — use an http(s) link ending in .png/.jpg/.webp/...");
                return;
            }
            urlDraft = u;
            PacketDistributor.sendToServer(new GraphicsUrlPayload(bePos, u));
        }
        onClose();
    }

    /** Ask the SERVER to fetch the URL and deliver it back as a preview. */
    private void requestPreview() {
        String u = urlInput.getValue().trim();
        if (u.isBlank()) { status = Component.literal("URL empty"); return; }
        if (!ImageUrlPolicy.isPlausibleImageUrl(u)) {
            status = Component.literal("Not an image URL — use an http(s) link ending in .png/.jpg/.webp/...");
            return;
        }
        urlDraft = u;
        releasePreview();
        previewRequestedAt = System.currentTimeMillis();
        status = Component.literal("Requesting from server...");
        FetchImagePayload.request(bePos, u);
    }

    /** Show the server-delivered image as a scaled preview (decode on worker thread). */
    private void showPreview(byte[] data) {
        previewRequestedAt = 0;
        status = Component.literal("Decoding...");
        CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
                if (src == null) return null;
                int pw = 310, ph = 130;
                float s = Math.min(Math.min((float) pw / src.getWidth(), (float) ph / src.getHeight()), 1f);
                int dw = Math.max(1, (int) (src.getWidth() * s));
                int dh = Math.max(1, (int) (src.getHeight() * s));
                BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
                var g = out.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                   java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, dw, dh, null);
                g.dispose();
                return out;
            } catch (Exception e) {
                return null;
            }
        }, ImageExecutors.CLIENT_DECODE).thenAccept(small -> Minecraft.getInstance().execute(() -> {
            if (small == null) { status = Component.literal("Preview failed (undecodable image)"); return; }
            releasePreview();
            NativeImage ni = new NativeImage(NativeImage.Format.RGBA, small.getWidth(), small.getHeight(), false);
            for (int y = 0; y < small.getHeight(); y++)
                for (int x = 0; x < small.getWidth(); x++) {
                    int a = small.getRGB(x, y);
                    ni.setPixelRGBA(x, y, (a>>24)<<24|(a&0xFF)<<16|((a>>8)&0xFF)<<8|(a>>16)&0xFF);
                }
            Minecraft.getInstance().getTextureManager().register(PREVIEW_TEX, new DynamicTexture(ni));
            previewId = PREVIEW_TEX; previewW = small.getWidth(); previewH = small.getHeight();
            status = Component.literal(previewW + "x" + previewH);
        }));
    }

    private void releasePreview() {
        if (previewId != null) { Minecraft.getInstance().getTextureManager().release(previewId); previewId = null; }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float p) {
        if (previewRequestedAt > 0 && previewId == null
                && System.currentTimeMillis() - previewRequestedAt > PREVIEW_TIMEOUT_MS) {
            previewRequestedAt = 0;
            status = Component.literal("Server did not deliver the image — check the URL and the server log");
        }
        super.render(g, mx, my, p);
        int cx = width/2, pw = 310, ph = 130;
        if (!tabCache) g.drawString(font, "Image URL:", cx - 155, 40, 0xAAAAAA);

        g.fill(cx - pw/2 - 1, 75, cx + pw/2 + 1, 75 + ph + 1, 0xFF444444);
        if (!tabCache && previewId != null) {
            float s = Math.min((float)pw/previewW, (float)ph/previewH);
            int dw = (int)(previewW*s), dh = (int)(previewH*s);
            g.blit(previewId, cx - dw/2, 75 + (ph-dh)/2, 0, 0, dw, dh, dw, dh);
        } else if (tabCache) {
            int cols = 4, tw = (pw - 12) / cols, th = tw;
            int x0 = cx - pw/2 + 3, y0 = 77;
            for (int i = 0; i < Math.min(cachedEntries.size(), 12); i++) {
                var e = cachedEntries.get(i);
                int tx = x0 + (i%cols)*(tw+3), ty = y0 + (i/cols)*(th+18);
                if (e.tex != null) {
                    g.fill(tx, ty, tx+tw, ty+th, 0xFF111111);
                    g.blit(e.tex, tx+1, ty+1, 0, 0, tw-2, th-2, tw-2, th-2);
                } else g.fill(tx, ty, tx+tw, ty+th, 0xFF333340);
                g.fill(tx, ty+th, tx+tw, ty+th+14, 0x88000000);
                g.drawString(font, e.w+"x"+e.h, tx+2, ty+th+3, 0xCCCCCC);
            }
            if (cachedEntries.isEmpty())
                g.drawCenteredString(font, "No cached images.", cx, 75+ph/2, 0x888888);
        } else {
            g.fill(cx - pw/2, 75, cx + pw/2, 75 + ph, 0xFF222222);
        }
        if (status != null) g.drawCenteredString(font, status, cx, 75+ph+5, 0xCCCCCC);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (tabCache && btn == 0) {
            int cx = width/2, pw = 310, cols = 4, tw = (pw-12)/cols, th = tw;
            int x0 = cx-pw/2+3, y0 = 77;
            for (int i = 0; i < Math.min(cachedEntries.size(), 12); i++) {
                int tx = x0+(i%cols)*(tw+3), ty = y0+(i/cols)*(th+18);
                if (mx>=tx && mx<=tx+tw && my>=ty && my<=ty+th+14) {
                    tabCache = false; rebuild();
                    urlDraft = cachedEntries.get(i).url;
                    urlInput.setValue(urlDraft);
                    requestPreview();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public void onClose() { releasePreview(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void onFilesDrop(List<Path> paths) {
        // Local file paths cannot be served across the network; only http(s)
        // image URLs are supported.
        if (!paths.isEmpty()) {
            urlDraft = "";
            if (urlInput != null) urlInput.setValue("");
            status = Component.literal("Local files are not supported — paste an http(s) image URL");
        }
    }
}