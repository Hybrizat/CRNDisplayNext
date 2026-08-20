package com.hybrizat.crndisplaynext.util;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * URL format policy for image downloads.
 *
 * <p>Whitelisting image hosts is not practical (images come from many
 * hosts/CDNs), so we validate the URL <i>shape</i> instead: only http(s) URLs
 * whose path ends with a known image extension are allowed to trigger a
 * download. This rejects HTML pages, API endpoints, local paths and other
 * non-image targets before any network I/O happens.</p>
 */
public final class ImageUrlPolicy {

    /** Image extensions that Java's ImageIO can decode (plus common web formats). */
    private static final Set<String> IMAGE_EXTENSIONS =
        Set.of("png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff", "webp");

    private ImageUrlPolicy() {}

    /**
     * @return true if the URL looks like a downloadable image resource.
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

        String path = uri.getPath();
        if (path == null || path.isEmpty()) return false;
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        return IMAGE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}