package com.hybrizat.crndisplaynext.client;

import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * AWT font loader with size-caching.
 *
 * Priority for Japanese text:
 *   1. User-provided ShinGo fonts (<gameDir>/crndisplaynext/fonts/ShinGoPr6N-*.otf)
 *   2. Bundled MPlus 2p fonts (mplus-2p-medium/bold/light.ttf)
 *   3. Noto Sans CJK / SansSerif fallback
 */
public final class FontLoader {

    private static final String ASSET_PREFIX = "/assets/crndisplaynext/fonts/";
    private static final String[] BUNDLED = {
        "mplus-2p-bold.ttf",
        "mplus-2p-medium.ttf",
        "mplus-2p-light.ttf",
        "HelveticaNeue-Bold.otf",
        "HelveticaNeue-Medium.otf",
        "HelveticaNeue-Roman.otf",
        "NeueFrutigerWorld-Bold.otf",
        "NotoSansCJKsc-Bold.otf",
        "NotoSansCJKsc-Regular.otf",
    };

    /** All registered fonts, keyed by fontName and family (lowercase). */
    private static final Map<String, Font> FONTS = new HashMap<>();
    private static final Map<Long, Font> cache = new HashMap<>();

    // User-provided ShinGo (priority 1)
    private static Font shingoMedium, shingoBold, shingoLight;
    // Bundled MPlus (priority 2)
    private static Font mplusMedium, mplusBold;

    public static void init(File gameDir) {
        // 1. Bundled first (user files may override the map by name)
        for (String f : BUNDLED) {
            Font font = tryLoad(f);
            if (font != null) register(font);
        }
        mplusMedium = find("m+ 2p medium", "mplus2p-medium", "m+ 2p");
        mplusBold = find("m+ 2p bold", "mplus2p-bold");
        System.out.println("[FontLoader] registered: " + FONTS.keySet());
        System.out.println("[FontLoader] mplusMedium=" + (mplusMedium != null ? mplusMedium.getFontName() : "NULL")
            + " mplusBold=" + (mplusBold != null ? mplusBold.getFontName() : "NULL"));

        // 2. User font folder — ShinGo files take top priority for Japanese
        Path fontDir = gameDir.toPath().resolve("crndisplaynext").resolve("fonts");
        try {
            if (Files.isDirectory(fontDir)) {
                try (var s = Files.newDirectoryStream(fontDir)) {
                    for (Path p : s) {
                        String name = p.getFileName().toString();
                        if (!(name.endsWith(".otf") || name.endsWith(".ttf"))) continue;
                        try {
                            Font font = Font.createFont(Font.TRUETYPE_FONT, p.toFile());
                            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                            register(font);
                            String low = name.toLowerCase(Locale.ROOT);
                            if (low.contains("shingo") || low.contains("shin-go")) {
                                if (shingoMedium == null && (low.contains("medium") || low.contains("regular") || low.contains("roman")))
                                    shingoMedium = font;
                                if (shingoBold == null && (low.contains("bold") || low.contains("heavy") || low.contains("black")))
                                    shingoBold = font;
                                if (shingoLight == null && low.contains("light"))
                                    shingoLight = font;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /** Exact name lookup (case-insensitive) — used for user fonts like ShinGoPr6N-Medium. */
    public static Font get(String... names) {
        for (String n : names) {
            if (n == null) continue;
            Font f = FONTS.get(n.toLowerCase(Locale.ROOT));
            if (f != null) return f;
        }
        // Fallback: no user ShinGo → bundled MPlus medium
        return mplusMedium;
    }

    /** Japanese medium weight: ShinGo (user) → MPlus → Noto → SansSerif. */
    public static Font medium(float size) {
        Font base = shingoMedium != null ? shingoMedium : (mplusMedium != null ? mplusMedium : FONTS.get("notosanscjksc-regular"));
        return derive(base, Font.PLAIN, size);
    }

    /** Japanese bold weight: ShinGo bold → MPlus bold → Noto bold → SansSerif bold. */
    public static Font bold(float size) {
        Font base = shingoBold != null ? shingoBold : (mplusBold != null ? mplusBold : FONTS.get("notosanscjksc-bold"));
        return derive(base, Font.BOLD, size);
    }

    /** Latin time font: HelveticaNeue-Bold → MPlus bold → SansSerif bold. */
    public static Font time(float size) {
        Font base = get("HelveticaNeue-Bold", "Helvetica Neue");
        return derive(base != null ? base : mplusBold, Font.BOLD, size);
    }

    /** Latin clock font: HelveticaNeue-Roman → Helvetica → SansSerif. */
    public static Font clock(float size) {
        Font base = get("HelveticaNeue-Roman", "Helvetica");
        return derive(base, Font.PLAIN, size);
    }

    /** Station-code badge typeface: Frutiger (NeueFrutigerWorld-Bold) → SansSerif bold. */
    public static Font badge(float size) {
        Font base = get("NeueFrutigerWorld-Bold", "Frutiger");
        return derive(base, Font.BOLD, size);
    }

    private static Font find(String... substrings) {
        for (Map.Entry<String, Font> e : FONTS.entrySet()) {
            for (String s : substrings) {
                if (e.getKey().contains(s)) return e.getValue();
            }
        }
        return null;
    }

    private static void register(Font f) {
        FONTS.put(f.getFontName().toLowerCase(Locale.ROOT), f);
        FONTS.put(f.getFamily().toLowerCase(Locale.ROOT), f);
            ;
    }

    private static Font tryLoad(String filename) {
        try (InputStream is = FontLoader.class.getResourceAsStream(ASSET_PREFIX + filename)) {
            if (is != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, is);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
                return f;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Font derive(Font base, int fallbackStyle, float size) {
        long key = ((long)(base == null ? fallbackStyle : System.identityHashCode(base)) << 16)
                 | (long)(int)(size * 10);
        return cache.computeIfAbsent(key, _k -> {
            if (base != null) return base.deriveFont(size);
            return new Font("SansSerif", fallbackStyle, (int)size);
        });
    }
}
