package com.hybrizat.crndisplaynext.util;

import java.net.URI;
import java.util.Locale;

/**
 * URL policy for image downloads.
 *
 * <p>Whitelisting image hosts or file extensions is not practical: images
 * come from many hosts/CDNs, and many legitimate image URLs carry no file
 * extension at all (API-style links, CDN tokens, redirect endpoints, ...).
 * So we validate only what a player-supplied URL must satisfy before the
 * server may fetch it:
 *
 * <ul>
 *   <li>http/https scheme with a real host,</li>
 *   <li>no loopback / link-local / cloud-metadata targets (SSRF guard);
 *       LAN hosts stay allowed for local image servers.</li>
 * </ul>
 *
 * <p>The actual image-format check happens server-side: every download is
 * decoded with ImageIO and rejected when undecodable or oversized
 * (see {@code ServerImageCache}), so non-image content never reaches a
 * client regardless of what the URL looks like.</p>
 */
public final class ImageUrlPolicy {

    private ImageUrlPolicy() {}

    /**
     * @return true if the URL is allowed to trigger a server-side fetch.
     */
    public static boolean isPlausibleImageUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        if (u.isEmpty()) return false;

        URI uri;
        try {
            uri = URI.create(u);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return false;

        if (uri.getHost() == null || uri.getHost().isEmpty()) return false;

        // Block loopback / link-local / well-known cloud-metadata targets
        // (server-side fetches must not be steered at internal services).
        // LAN hosts (192.168.x, 10.x, 172.16-31.x) remain allowed for local image servers.
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1); // [::1] form
        if (host.equals("localhost") || host.equals("::1")) return false;
        if (host.startsWith("127.") || host.startsWith("169.254.")) return false;
        if (host.equals("100.100.100.200") || host.equals("metadata.google.internal")
                || host.equals("instance-data")) return false;

        return true;
    }
}
