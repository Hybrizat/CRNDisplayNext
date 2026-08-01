package com.hybrizat.crndisplaynext.client.screen;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.network.GraphicsUrlPayload;
import com.hybrizat.crndisplaynext.network.RequestCachePayload;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GraphicsImageScreen extends Screen {

    private static final ResourceLocation PREVIEW_TEX =
        ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "gfx_preview");

    // Called by CacheListPayload handler to deliver data to the open screen
    public static void onCacheData(List<String> rawEntries) {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof GraphicsImageScreen gs) {
            gs.setCacheData(rawEntries);
            CRNDisplayNextMod.LOGGER.info("[GUI] Injected {} cache entries into open screen", rawEntries.size());
        }
    }

    private final BlockPos bePos;
    private EditBox urlInput;
    private ResourceLocation previewId;
    private int previewW, previewH;
    private Component status;
    private boolean tabCache;

    // Cached entries with textures (instance-local, avoids classloader issues)
    private record Cached(String id, String url, int w, int h, ResourceLocation tex) {}
    private final List<Cached> cachedEntries = new ArrayList<>();
    private final Map<String, ResourceLocation> thumbs = new HashMap<>();
    private int texIdx;

    public GraphicsImageScreen(BlockPos bePos, String currentUrl) {
        super(Component.literal("Image Settings"));
        this.bePos = bePos;
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
            addRenderableWidget(urlInput);
            addRenderableWidget(Button.builder(Component.literal("Load"), b -> loadPreview())
                .pos(cx + 160, top + 28).size(50, 20).build());
        }
    }

    void rebuild() { clearWidgets(); init(); }

    private void apply() {
        if (!tabCache && urlInput != null) {
            String u = urlInput.getValue().trim();
            if (!u.isBlank()) PacketDistributor.sendToServer(new GraphicsUrlPayload(bePos, u));
        }
        onClose();
    }

    private void loadPreview() {
        String u = urlInput.getValue().trim();
        if (u.isBlank()) { status = Component.literal("URL empty"); return; }
        loadDirect(u);
    }

    private void loadDirect(String url) {
        status = Component.literal("Loading...");
        CompletableFuture.supplyAsync(() -> {
            try {
                var c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
                var r = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
                var resp = c.send(r, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) return null;
                return ImageIO.read(resp.body());
            } catch (Exception e) { return null; }
        }).thenAccept(img -> Minecraft.getInstance().execute(() -> {
            if (img == null) { status = Component.literal("Failed"); return; }
            releasePreview();
            NativeImage ni = new NativeImage(NativeImage.Format.RGBA, img.getWidth(), img.getHeight(), false);
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++) {
                    int a = img.getRGB(x, y);
                    ni.setPixelRGBA(x, y, (a>>24)<<24|(a&0xFF)<<16|((a>>8)&0xFF)<<8|(a>>16)&0xFF);
                }
            Minecraft.getInstance().getTextureManager().register(PREVIEW_TEX, new DynamicTexture(ni));
            previewId = PREVIEW_TEX; previewW = img.getWidth(); previewH = img.getHeight();
            status = Component.literal(previewW + "x" + previewH);
        }));
    }

    private void releasePreview() {
        if (previewId != null) { Minecraft.getInstance().getTextureManager().release(previewId); previewId = null; }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float p) {
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
                    urlInput.setValue(cachedEntries.get(i).url);
                    loadDirect(cachedEntries.get(i).url);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public void onClose() { releasePreview(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void onFilesDrop(List<Path> paths) {
        for (Path p : paths) {
            try { if (Files.exists(p)) { urlInput.setValue(p.toUri().toString()); loadPreview(); return; } }
            catch (Exception e) {}
        }
    }
}
