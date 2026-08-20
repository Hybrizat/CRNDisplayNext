package com.hybrizat.crndisplaynext.server;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.util.ImageUrlPolicy;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server-side image cache. Downloads images from URLs on a dedicated worker
 * thread, stores them to disk, and provides a listing for client browsing.
 *
 * <p>Thread model: HTTP I/O and image decoding run on the "crn-image-download"
 * thread, so the server main thread is never blocked. Callers that must send
 * packets with the result should hop back via {@code server.execute(...)}
 * (see FetchImagePayload).</p>
 */
public class ServerImageCache {

    /** Max image bytes transferred to a client (16 MiB). */
    public static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;
    /** Chunk size for network transfer (64 KiB per packet). */
    public static final int CHUNK_SIZE = 64 * 1024;
    private static final int MAX_DIM = 4096;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    /** Dedicated download thread — keeps HTTP I/O off the server main thread. */
    private static final ExecutorService DOWNLOAD_EXEC =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "crn-image-download");
            t.setDaemon(true);
            return t;
        });

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

    /** @return true if the URL passed the image-format policy gate. */
    private static boolean isDownloadable(String url) {
        if (!ImageUrlPolicy.isPlausibleImageUrl(url)) {
            CRNDisplayNextMod.LOGGER.warn("[Cache] refusing non-image URL: {}", url);
            return false;
        }
        return true;
    }

    /** Download image from URL and cache to disk (async). Returns cache entry or null. */
    public CompletableFuture<CacheEntry> downloadAndCache(String url) {
        if (!isDownloadable(url)) return CompletableFuture.completedFuture(null);
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
                    CRNDisplayNextMod.LOGGER.warn("[Cache] HTTP {} for {}", resp.statusCode(), url);
                    return null;
                }
                BufferedImage img = ImageIO.read(resp.body());
                if (img == null || img.getWidth() > MAX_DIM || img.getHeight() > MAX_DIM) {
                    CRNDisplayNextMod.LOGGER.warn("[Cache] undecodable or oversized image ({}x{}): {}",
                        img == null ? 0 : img.getWidth(), img == null ? 0 : img.getHeight(), url);
                    return null;
                }

                // Save image
                ImageIO.write(img, "PNG", imgFile.toFile());
                if (Files.size(imgFile) > MAX_IMAGE_BYTES) {
                    CRNDisplayNextMod.LOGGER.warn("[Cache] image exceeds {} bytes, rejecting: {}",
                        MAX_IMAGE_BYTES, url);
                    Files.deleteIfExists(imgFile);
                    return null;
                }
                CRNDisplayNextMod.LOGGER.info("[Cache] Saved: {} ({}x{})", id, img.getWidth(), img.getHeight());

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
            } catch (Exception e) { CRNDisplayNextMod.LOGGER.error("[Cache] Download failed for {}: {}", url, e.toString()); return null; }
        }, DOWNLOAD_EXEC);
    }

    /** Cache-first: return cached bytes or download+return (async). */
    public CompletableFuture<byte[]> getOrDownload(String url) {
        if (!ImageUrlPolicy.isPlausibleImageUrl(url)) return CompletableFuture.completedFuture(null);
        String id = hash(url);
        Path imgFile = cacheDir.resolve(id + ".png");
        if (Files.exists(imgFile)) {
            // Disk read off the main thread as well.
            return CompletableFuture.supplyAsync(() -> {
                try { return Files.readAllBytes(imgFile); }
                catch (IOException e) { return null; }
            }, DOWNLOAD_EXEC);
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