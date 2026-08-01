package com.hybrizat.crndisplaynext.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Client-side cache data with thumbnails. */
public class ClientCacheData {

    public record ThumbEntry(String id, String url, int w, int h, ResourceLocation tex) {}

    private static final List<ThumbEntry> entries = new ArrayList<>();
    private static final Map<String, ResourceLocation> texMap = new HashMap<>();
    private static int texCounter;

    public static List<ThumbEntry> getEntries() { return entries; }

    public static void setFromStrings(List<String> strings) {
        // Release old textures
        for (ResourceLocation rl : texMap.values())
            Minecraft.getInstance().getTextureManager().release(rl);
        texMap.clear(); entries.clear();

        for (String s : strings) {
            String[] parts = s.split("\t");
            if (parts.length < 5) continue;
            String id = parts[0], url = parts[1];
            int w = Integer.parseInt(parts[2]), h = Integer.parseInt(parts[3]);
            String b64 = parts[4];

            ResourceLocation tex = null;
            if (!b64.isEmpty()) {
                try {
                    byte[] data = Base64.getDecoder().decode(b64);
                    NativeImage ni = NativeImage.read(
                        new java.io.ByteArrayInputStream(data));
                    var dt = new DynamicTexture(ni);
                    tex = ResourceLocation.fromNamespaceAndPath(
                        "crndisplaynext", "cache_thumb/" + (texCounter++));
                    Minecraft.getInstance().getTextureManager().register(tex, dt);
                    texMap.put(id, tex);
                } catch (Exception e) {}
            }
            entries.add(new ThumbEntry(id, url, w, h, tex));
        }
    }
}
