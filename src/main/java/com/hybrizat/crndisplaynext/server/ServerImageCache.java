package com.hybrizat.crndisplaynext.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Server-side image cache. Downloads images from URLs, stores to disk,
 * and provides a listing for client browsing.
 */
public class ServerImageCache {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public record CacheEntry(String id, String url, int width, int height, long timestamp) {}

    private final Path cacheDir;

    public ServerImageCache(Path worldDir) {
        this.cacheDir = worldDir.resolve("crndisplaynext").resolve("cache");
        try { Files.createDirectories(cacheDir); } catch (IOException e) {}
    }

    public static ServerImageCache get(MinecraftServer server) {
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        return new ServerImageCache(worldDir);
    }

    /** Hash URL to a cache ID */
    public static String hash(String url) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(url.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString().substring(0, 16);
        } catch (Exception e) { return Integer.toHexString(url.hashCode()); }
    }

    /** Download image from URL and cache to disk. Returns cache entry or null. */
    public CompletableFuture<CacheEntry> downloadAndCache(String url) {
        String id = hash(url);
        Path imgFile = cacheDir.resolve(id + ".png");
        Path metaFile = cacheDir.resolve(id + ".meta");

        // Already cached?
        if (Files.exists(imgFile) && Files.exists(metaFile)) {
            try {
                return CompletableFuture.completedFuture(readMeta(metaFile));
            } catch (Exception e) {}
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                var req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15)).GET().build();
                var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) {
                    System.err.println("[Cache] HTTP " + resp.statusCode() + " for " + url);
                    return null;
                }
                BufferedImage img = ImageIO.read(resp.body());
                if (img == null || img.getWidth() > 4096 || img.getHeight() > 4096) return null;

                // Save image
                ImageIO.write(img, "PNG", imgFile.toFile());
                System.err.println("[Cache] Saved: " + id + " (" + img.getWidth() + "x" + img.getHeight() + ")");

                // Save thumbnail (64×64 max)
                int tw = Math.min(64, img.getWidth());
                int th = Math.min(64, img.getHeight());
                BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
                var g2 = thumb.createGraphics();
                g2.drawImage(img.getScaledInstance(tw, th, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
                g2.dispose();
                Path thumbFile = cacheDir.resolve(id + "_thumb.png");
                ImageIO.write(thumb, "PNG", thumbFile.toFile());

                // Save metadata
                CacheEntry entry = new CacheEntry(id, url, img.getWidth(), img.getHeight(),
                    System.currentTimeMillis());
                writeMeta(metaFile, entry);
                return entry;
            } catch (Exception e) { return null; }
        });
    }

    /** Cache-first: return cached bytes or download+return. */
    public CompletableFuture<byte[]> getOrDownload(String url) {
        String id = hash(url);
        Path imgFile = cacheDir.resolve(id + ".png");
        if (Files.exists(imgFile)) {
            try {
                return CompletableFuture.completedFuture(Files.readAllBytes(imgFile));
            } catch (IOException e) {}
        }
        return downloadAndCache(url).thenCompose(entry -> {
            if (entry == null) return CompletableFuture.completedFuture(null);
            try { return CompletableFuture.completedFuture(getImageBytes(entry.id())); }
            catch (Exception e) { return CompletableFuture.completedFuture(null); }
        });
    }

    /** Get image bytes from cache */
    public byte[] getImageBytes(String id) throws IOException {
        return Files.readAllBytes(cacheDir.resolve(id + ".png"));
    }

    /** Get thumbnail bytes */
    public byte[] getThumbBytes(String id) throws IOException {
        return Files.readAllBytes(cacheDir.resolve(id + "_thumb.png"));
    }

    /** List all cached entries */
    public List<CacheEntry> listEntries() {
        List<CacheEntry> list = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(cacheDir, "*.meta")) {
            for (Path p : stream) {
                try { list.add(readMeta(p)); } catch (Exception e) {}
            }
        } catch (IOException e) {}
        list.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));
        return list;
    }

    private CacheEntry readMeta(Path p) throws IOException {
        var props = new java.util.Properties();
        try (var in = Files.newInputStream(p)) { props.load(in); }
        return new CacheEntry(
            p.getFileName().toString().replace(".meta", ""),
            props.getProperty("url", ""),
            Integer.parseInt(props.getProperty("w", "0")),
            Integer.parseInt(props.getProperty("h", "0")),
            Long.parseLong(props.getProperty("ts", "0"))
        );
    }

    private void writeMeta(Path p, CacheEntry e) throws IOException {
        var props = new java.util.Properties();
        props.setProperty("url", e.url());
        props.setProperty("w", String.valueOf(e.width()));
        props.setProperty("h", String.valueOf(e.height()));
        props.setProperty("ts", String.valueOf(e.timestamp()));
        try (var out = Files.newOutputStream(p)) { props.store(out, "CRN Display Next cache"); }
    }
}
