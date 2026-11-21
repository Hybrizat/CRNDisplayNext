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
 *   3. Bundled Noto Sans CJK — fills the gaps M+ does not cover
 *   4. The system default face, for anything the above are missing
 *
 * The same order is used per glyph in {@link #resolveFor}, so a ShinGo install
 * still falls back to M+, then Noto, then the system font.
 */
public final class FontLoader {

    private static final String ASSET_PREFIX = "/assets/crndisplaynext/fonts/";
    private static final String[] BUNDLED = {
        "mplus-2p-bold.ttf",
        "mplus-2p-medium.ttf",
        "mplus-2p-light.ttf",
        // SIL OFL stand-in for Helvetica Neue (metrically Arial-compatible, so the
        // clock keeps its proportions). Helvetica itself is not distributed.
        "LiberationSans-Regular.ttf",
        "LiberationSans-Bold.ttf",
        "NotoSansCJKsc-Bold.otf",
        "NotoSansCJKsc-Regular.otf",
    };

    /** All registered fonts, keyed by fontName and family (lowercase). */
    private static final Map<String, Font> FONTS = new HashMap<>();
    private static final Map<Long, Font> cache = new HashMap<>();
    /** System default faces, keyed by style + size (see {@link #logicalFor}). */
    private static final Map<Long, Font> LOGICAL_CACHE = new HashMap<>();

    // User-provided ShinGo (priority 1)
    private static Font shingoMedium, shingoBold, shingoLight;
    // Bundled MPlus (priority 2)
    private static Font mplusMedium, mplusBold;
    // Bundled Noto (CJK coverage - per-char fallback target)
    private static Font notoBold, notoRegular;
    // Latin faces: bundled Liberation Sans, replaceable by a user Helvetica/Arial
    private static Font latinRegular, latinBold;

    public static void init(File gameDir) {
        // 1. Bundled first (user files may override the map by name)
        for (String f : BUNDLED) {
            Font font = tryLoad(f);
            if (font != null) {
                register(font);
                // Keep the CJK fallback faces by file, not by name lookup: the map is
                // keyed on the font's own name ("Noto Sans CJK SC Bold"), so a lookup
                // for "notosanscjksc-bold" never matched and every char the main face
                // lacked came out as tofu.
                if (f.equals("NotoSansCJKsc-Bold.otf")) notoBold = font;
                if (f.equals("NotoSansCJKsc-Regular.otf")) notoRegular = font;
                if (f.equals("mplus-2p-medium.ttf")) mplusMedium = font;
                if (f.equals("mplus-2p-bold.ttf")) mplusBold = font;
                if (f.equals("LiberationSans-Regular.ttf")) latinRegular = font;
                if (f.equals("LiberationSans-Bold.ttf")) latinBold = font;
            }
        }
        if (mplusMedium == null) mplusMedium = find("m+ 2p medium", "mplus2p-medium", "m+ 2p");
        if (mplusBold == null) mplusBold = find("m+ 2p bold", "mplus2p-bold");
        System.out.println("[FontLoader] registered: " + FONTS.keySet());
        System.out.println("[FontLoader] mplusMedium=" + (mplusMedium != null ? mplusMedium.getFontName() : "NULL")
            + " mplusBold=" + (mplusBold != null ? mplusBold.getFontName() : "NULL")
            + " notoBold=" + (notoBold != null ? notoBold.getFontName() : "NULL")
            + " notoRegular=" + (notoRegular != null ? notoRegular.getFontName() : "NULL")
            + " latinRegular=" + (latinRegular != null ? latinRegular.getFontName() : "NULL")
            + " latinBold=" + (latinBold != null ? latinBold.getFontName() : "NULL"));

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
                            // Helvetica / Arial are not distributed either. A licensed
                            // copy in this folder replaces the bundled Liberation Sans,
                            // which is metrically compatible with both.
                            if (low.contains("helvetica") || low.contains("arial") || low.contains("liberation")) {
                                if (looksBold(low, font)) latinBold = font;
                                else latinRegular = font;
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
        Font base = shingoMedium != null ? shingoMedium : (mplusMedium != null ? mplusMedium : notoRegular);
        return derive(base, Font.PLAIN, size);
    }

    /** Japanese bold weight: ShinGo bold → MPlus bold → Noto bold → SansSerif bold. */
    public static Font bold(float size) {
        Font base = shingoBold != null ? shingoBold : (mplusBold != null ? mplusBold : notoBold);
        return derive(base, Font.BOLD, size);
    }

    /** Latin time font: user Helvetica/Arial → bundled Liberation Sans Bold. */
    public static Font time(float size) {
        return derive(latinBold, Font.BOLD, size);
    }

    /** Latin clock font: user Helvetica/Arial → bundled Liberation Sans Regular. */
    public static Font clock(float size) {
        return derive(latinRegular, Font.PLAIN, size);
    }

    /**
     * Station-code badge typeface. Neue Frutiger World is not distributed; the
     * humanist Latin of the bundled Noto Sans is the closest stand-in, and a
     * user-supplied Frutiger file takes precedence.
     */
    public static Font badge(float size) {
        Font base = find("frutiger");
        if (base == null) base = notoBold != null ? notoBold : notoRegular;
        return derive(base, Font.BOLD, size);
    }

    /** Whether a user font file is a bold face, by file name and by font name. */
    private static boolean looksBold(String fileName, Font font) {
        if (fileName.contains("bold") || fileName.contains("heavy") || fileName.contains("black")) return true;
        String name = font.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT);
        return name.contains("bold") || name.contains("heavy") || name.contains("black");
    }

    /** Can this font render the character at all (no tofu)? */
    public static boolean canDisplay(Font font, char c) { return font != null && font.canDisplay(c); }

    /**
     * Resolve a font that can actually draw the given character.
     *
     * <p>Only the gaps of the primary face are filled; the text stays in the
     * primary for every character it has. The face order is the same one used to
     * choose the primary: bundled M+ first, then the bundled Noto CJK face, then
     * the JVM's logical font. The logical font is the last resort because the JVM
     * resolves it through the operating system's own font linking, so anything the
     * system can print wins over a missing-glyph box. Only when even that has no
     * glyph do we return the primary and let it show its tofu.</p>
     */
    public static Font resolveFor(Font primary, char c) {
        if (primary == null) return null;
        if (primary.canDisplay(c)) return primary;

        boolean bold = wantsBold(primary);

        // M+ comes before Noto so a ShinGo install still gets the same face for a
        // gap that M+ can fill, instead of mixing in Noto glyphs first.
        Font mplus = bold
            ? (mplusBold != null ? mplusBold : mplusMedium)
            : (mplusMedium != null ? mplusMedium : mplusBold);
        if (mplus != null && mplus.canDisplay(c)) return derive(mplus, Font.PLAIN, primary.getSize2D());

        Font noto = bold
            ? (notoBold != null ? notoBold : notoRegular)
            : (notoRegular != null ? notoRegular : notoBold);
        if (noto != null && noto.canDisplay(c)) return derive(noto, Font.PLAIN, primary.getSize2D());

        Font logical = logicalFor(primary);
        if (logical.canDisplay(c)) return logical;

        return primary;
    }

    /**
     * Whether a face is meant to look bold. The bundled faces are separate bold
     * files whose {@code getStyle()} is PLAIN, so the style bit alone would pick the
     * regular fallback for a bold station name.
     */
    private static boolean wantsBold(Font primary) {
        return (primary.getStyle() & Font.BOLD) != 0
            || primary.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT).contains("bold");
    }

    /**
     * The system default face at the primary's size and weight, cached per size.
     * Java's logical fonts fall back across the installed system fonts, so this is
     * the broadest coverage available and the right last resort for a stray kanji
     * the bundled faces are missing.
     */
    private static Font logicalFor(Font primary) {
        boolean bold = wantsBold(primary);
        long key = ((long) (bold ? 1 : 0) << 40) | Math.round(primary.getSize2D() * 10);
        return LOGICAL_CACHE.computeIfAbsent(key,
            _k -> new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 1)
                .deriveFont(primary.getSize2D()));
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
