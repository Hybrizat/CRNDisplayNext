package com.hybrizat.crndisplaynext.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages NativeImage + DynamicTexture for the graphics display.
 * Async HTTP download → BufferedImage → NativeImage → DynamicTexture.
 */
public class DynamicTextureHolder implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicTextureHolder.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static final int MAX_DIM = 4096;

    private NativeImage image;
    private DynamicTexture texture;
    private ResourceLocation id;
    private int width, height;
    private boolean ready, closed;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private String lastUrl = "";

    public DynamicTextureHolder(int w, int h) {
        this.width = w; this.height = h;
        allocate(w, h);
    }

    private void allocate(int w, int h) {
        freeGl();
        width = w; height = h;
        image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        fillColor(0x00000000);
        texture = new DynamicTexture(image);
        id = Minecraft.getInstance().getTextureManager()
            .register("crndisplaynext/gfx_", texture);
    }

    public void resize(int w, int h, String reloadUrl) {
        if (w == width && h == height) return;
        allocate(w, h);
        if (reloadUrl != null && !reloadUrl.isBlank()) {
            lastUrl = ""; // force reload
            loadUrl(reloadUrl);
        }
    }

    private void freeGl() {
        if (texture != null) { Minecraft.getInstance().getTextureManager().release(id); texture.close(); texture = null; }
        if (image != null) { image.close(); image = null; }
        ready = false;
    }

    public void markReady() { if (!closed) ready = true; }

    public void fillColor(int argb) {
        if (image == null) return;
        int abgr = argbToAbgr(argb);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                image.setPixelRGBA(x, y, abgr);
    }

    public void copyFromBuffered(BufferedImage src) {
        if (image == null || src == null) return;
        BufferedImage scaled;
        if (src.getWidth() == width && src.getHeight() == height) {
            scaled = src;
        } else {
            scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            var g = scaled.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                               java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, width, height, null);
            g.dispose();
        }
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                image.setPixelRGBA(x, y, argbToAbgr(scaled.getRGB(x, y)));
    }

    public void upload() { if (texture != null) texture.upload(); }

    public void loadUrl(String url) {
        if (url == null || url.isBlank()) return;
        if (url.equals(lastUrl) && (loading.get() || ready)) return;
        if (closed) return;
        lastUrl = url;
        loading.set(true); ready = false;

        var req = HttpRequest.newBuilder().uri(URI.create(url))
            .timeout(Duration.ofSeconds(15)).GET().build();

        CompletableFuture.supplyAsync(() -> {
            try {
                var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) { LOG.warn("HTTP {} for {}", resp.statusCode(), url); return null; }
                var bi = ImageIO.read(resp.body());
                if (bi == null) { LOG.warn("Cannot decode: {}", url); return null; }
                if (bi.getWidth() > MAX_DIM || bi.getHeight() > MAX_DIM) {
                    LOG.warn("Image too large ({}×{}): {}", bi.getWidth(), bi.getHeight(), url);
                    return null;
                }
                return bi;
            } catch (Exception e) { LOG.error("Failed to load {}: {}", url, e.getMessage()); return null; }
        }).thenAccept(bi -> Minecraft.getInstance().execute(() -> {
            loading.set(false);
            if (closed || bi == null) return;
            copyFromBuffered(bi); upload(); ready = true;
        }));
    }

    public boolean isReady() { return ready && !closed; }
    public void loadBytes(byte[] data) {
        if (closed) return;
        try {
            var bi = ImageIO.read(new ByteArrayInputStream(data));
            if (bi == null) return;
            ready = false;
            Minecraft.getInstance().execute(() -> {
                copyFromBuffered(bi); upload(); ready = true;
            });
        } catch (Exception e) { LOG.error("Failed to load bytes", e); }
    }
    public ResourceLocation getId() { return id; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public static int argbToAbgr(int argb) {
        int a = (argb>>24)&0xFF, r = (argb>>16)&0xFF, g = (argb>>8)&0xFF, b = argb&0xFF;
        return (a<<24)|(b<<16)|(g<<8)|r;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        Minecraft.getInstance().execute(this::freeGl);
    }
}
