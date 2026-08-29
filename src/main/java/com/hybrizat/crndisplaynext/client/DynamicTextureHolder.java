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
import java.util.concurrent.CompletableFuture;

/**
 * Manages NativeImage + DynamicTexture for the graphics display.
 *
 * <p>All downloads are server-driven (see FetchImagePayload / ImageDataPayload);
 * this class only receives bytes. Decoding runs on the worker thread
 * {@link ImageExecutors#CLIENT_DECODE}; the GL upload stays on the main thread.</p>
 */
public class DynamicTextureHolder implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicTextureHolder.class);

    private NativeImage image;
    private DynamicTexture texture;
    private ResourceLocation id;
    private int width, height;
    private boolean ready, closed;

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
        id = Minecraft.getInstance().getTextureManager().register("crndisplaynext/gfx_", texture);
    }

    /** Reallocate the texture; the server re-delivers the image on next request. */
    public void resize(int w, int h) {
        if (w == width && h == height) return;
        allocate(w, h);
    }

    private void freeGl() {
        if (texture != null) { Minecraft.getInstance().getTextureManager().release(id); texture.close(); texture = null; }
        if (image != null) { image.close(); image = null; }
        ready = false;
    }

    public void markReady() { if (!closed) ready = true; }

    // NOTE: NativeImage (MC 1.21.1) stores pixels in native memory; getPixelsRGBA()
    // returns a throwaway copy, so bulk array writes are a silent no-op.
    // Per-pixel setPixelRGBA is the correct write path - do not 'optimize' this away.
    public void fillColor(int argb) {
        if (image == null) return;
        int abgr = argbToAbgr(argb);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                image.setPixelRGBA(x, y, abgr);
    }

    // Same native-memory caveat as fillColor: write per pixel via setPixelRGBA only.
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

    /**
     * Decode server-delivered image bytes on the worker thread, then copy +
     * upload on the main thread.
     */
    public void loadBytes(byte[] data) {
        if (closed || data == null || data.length == 0) return;
        ready = false;
        CompletableFuture.runAsync(() -> {
            BufferedImage bi;
            try {
                bi = ImageIO.read(new ByteArrayInputStream(data));
            } catch (Exception e) {
                LOG.error("Failed to decode image bytes", e);
                return;
            }
            if (bi == null) { LOG.warn("Cannot decode delivered image bytes"); return; }
            Minecraft.getInstance().execute(() -> {
                if (closed) return;
                copyFromBuffered(bi);
                upload();
                ready = true;
            });
        }, ImageExecutors.CLIENT_DECODE);
    }

    public boolean isReady() { return ready && !closed; }
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