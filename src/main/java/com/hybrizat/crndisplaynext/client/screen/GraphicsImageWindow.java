package com.hybrizat.crndisplaynext.client.screen;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.client.DynamicTextureHolder;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
import com.hybrizat.crndisplaynext.network.FetchImagePayload;
import com.hybrizat.crndisplaynext.network.RequestCachePayload;
import com.hybrizat.crndisplaynext.util.ImageUrlPolicy;
import com.mojang.blaze3d.platform.NativeImage;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLEditableLabel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.Padding;
import de.mrjulsen.mcdragonlib.client.util.DLGuiGraphics;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils;
import de.mrjulsen.mcdragonlib.client.util.GuiUtils.TextureFillMode;
import de.mrjulsen.mcdragonlib.data.ETextAlignment;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import de.mrjulsen.mcdragonlib.util.math.Rectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Image picker for the {@code jre_graphics} (Custom Image Display) type.
 *
 * <p>This is a DragonLib window, not a vanilla {@code Screen}. Opened from the
 * CRN settings window it is registered as a modal of that window's
 * {@link DLWindowManager}, so closing it just pops the modal and reveals the
 * settings window that stayed open underneath. The old implementation opened a
 * vanilla screen and reopened the settings window from that screen's close
 * callback, which produced a screen whose "previous screen" was the image screen
 * itself — so returning from the image screen left the settings window
 * unclosable.</p>
 *
 * <p>The edited URL is written into the live {@link GraphicsDisplaySettings}
 * instance owned by the settings window; that window persists its settings
 * through CRN's own {@code AdvancedDisplayUpdatePacketData} when it is confirmed.
 * That packet handles plain blocks and contraptions alike, so the image can now
 * be configured on a materialised train instead of only before assembly.</p>
 *
 * <p>Image I/O stays server-driven: "Load" asks the server to fetch the URL
 * (cache or download) and returns the bytes as a preview, so the client never
 * opens its own HTTP connections.</p>
 */
public class GraphicsImageWindow extends DLWindow {

    // ── Layout, all in window-local coordinates ────────────────────────
    private static final int WIN_W = 360;
    private static final int WIN_H = 268;
    private static final int PAD = 6;
    private static final int ROW_H = 16;
    private static final int BTN_W = 50;

    private static final int TITLE_Y = 5;
    private static final int TABS_Y = 17;
    private static final int ROW_Y = TABS_Y + ROW_H + 5;
    private static final int CANVAS_X = PAD;
    private static final int CANVAS_Y = ROW_Y + ROW_H + 5;
    private static final int CANVAS_W = WIN_W - PAD * 2;
    private static final int STATUS_Y = WIN_H - 12;
    private static final int CANVAS_H = STATUS_Y - 6 - CANVAS_Y;

    private static final int COLS = 4;
    private static final int MAX_THUMBS = 16;

    private static final DLColor WINDOW_BG = DLColor.fromInt(0xF01A1A20);
    private static final DLColor CANVAS_BG = DLColor.fromInt(0xFF121216);
    private static final DLColor BORDER = DLColor.fromInt(0xFF6A6A72);
    private static final DLColor TEXT_DIM = DLColor.fromInt(0xFF9A9AA2);
    private static final DLColor TEXT_STATUS = DLColor.fromInt(0xFFCCCCCC);
    private static final DLColor THUMB_BG = DLColor.fromInt(0xFF0C0C10);

    private static final long PREVIEW_TIMEOUT_MS = 10_000L;

    /** The open picker, if any, so payload handlers can route data into it. */
    private static GraphicsImageWindow openInstance;

    /** Called by the {@code ImageDataPayload} handler once a full image arrived. */
    public static void onImageData(BlockPos pos, byte[] data) {
        GraphicsImageWindow win = openInstance;
        // A null pos means the picker could not resolve its display: it still
        // works as a browser, but it cannot claim a specific display's image.
        if (win != null && win.bePos != null && win.bePos.equals(pos)) {
            win.showPreview(data);
        }
    }

    /** Called by the {@code CacheListPayload} handler with the server cache listing. */
    public static void onCacheData(List<String> rawEntries) {
        GraphicsImageWindow win = openInstance;
        if (win != null) {
            win.setCacheData(rawEntries);
        }
    }

    /**
     * Opens the picker as a modal of {@code manager}'s screen — used by the
     * settings window, where it nests above the settings window.
     */
    public static void open(DLWindowManager manager, BlockPos pos,
                            GraphicsDisplaySettings settings, Runnable onApplied) {
        if (manager == null) {
            CRNDisplayNextMod.LOGGER.warn("[Gfx] no window manager; cannot open the image picker");
            return;
        }
        manager.createModal(mgr -> {
            GraphicsImageWindow win = new GraphicsImageWindow(mgr, pos, settings, onApplied);
            openInstance = win;
            return win;
        });
    }

    /** Opens the picker as the root window of its own screen (used by {@code /gfxgui}). */
    public static void openStandalone(BlockPos pos, GraphicsDisplaySettings settings, Runnable onApplied) {
        DLWindow.openWindow(mgr -> {
            GraphicsImageWindow win = new GraphicsImageWindow(mgr, pos, settings, onApplied);
            openInstance = win;
            return win;
        });
    }

    private final BlockPos bePos;
    /** Live settings instance owned by the caller; the URL is written here. */
    private final GraphicsDisplaySettings settings;
    /** Invoked after the URL changed so the caller can refresh its own widgets. */
    private final Runnable onApplied;

    private final List<Cached> cachedEntries = new ArrayList<>();
    private int thumbIdx;

    private DLEditableLabel urlField;
    private DLButton loadButton;
    private DLButton applyButton;

    private String urlDraft;
    private boolean tabCache;
    private Component status;
    private long previewRequestedAt;

    /** Preview texture; created lazily so an unused picker allocates nothing. */
    private DynamicTextureHolder preview;
    private boolean previewPending;
    private boolean released;

    /**
     * One cached server image. {@code tw}/{@code th} are the THUMBNAIL's real pixel
     * size, which is what the blit needs: the convenience overload of
     * {@code GuiUtils.drawTexture} assumes a 256x256 atlas, so drawing a 64x64
     * thumbnail with it samples a quarter of the texture and shows a wrong crop.
     */
    private record Cached(String id, String url, int w, int h, ResourceLocation tex, int tw, int th) {}

    public GraphicsImageWindow(DLWindowManager manager, BlockPos bePos,
                               GraphicsDisplaySettings settings, Runnable onApplied) {
        super(manager);
        this.bePos = bePos;
        this.settings = settings;
        this.onApplied = onApplied;
        this.urlDraft = settings == null || settings.getImageUrl() == null ? "" : settings.getImageUrl();
        setSize(WIN_W, WIN_H);
        windowSpawnPosition.set(WindowPosition.CENTER);
        init();
    }

    @Override
    public void close() {
        // Persisting is the caller's job (it owns the settings object), so all
        // that is needed here is to make sure the edited value is in place.
        release();
        applyUrl(false);
    }

    private void release() {
        if (released) {
            return;
        }
        released = true;
        if (openInstance == this) {
            openInstance = null;
        }
        if (preview != null) {
            preview.close();
            preview = null;
        }
        releaseThumbs();
    }

    private void releaseThumbs() {
        for (Cached c : cachedEntries) {
            if (c.tex() != null) {
                try {
                    Minecraft.getInstance().getTextureManager().release(c.tex());
                } catch (Exception ignored) {
                    // already released
                }
            }
        }
        cachedEntries.clear();
    }

    private void init() {
        DLButton urlTab = addComponent(new DLButton(PAD, TABS_Y, BTN_W, ROW_H));
        urlTab.text.set(TextUtils.text("URL"));
        urlTab.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            tabCache = false;
            refreshTabs();
            return false;
        });

        DLButton cacheTab = addComponent(new DLButton(PAD + BTN_W + 3, TABS_Y, BTN_W, ROW_H));
        cacheTab.text.set(TextUtils.text("Cached"));
        cacheTab.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            tabCache = true;
            refreshTabs();
            RequestCachePayload.request();
            return false;
        });

        int fieldW = WIN_W - PAD * 2 - (BTN_W + 3) * 2;
        urlField = addComponent(new DLEditableLabel(PAD, ROW_Y, fieldW, ROW_H));
        urlField.text.set(urlDraft);
        urlField.padding.set(new Padding(3, 4, 3, 4));
        urlField.addEventListener(DLEditableLabel.TextEditedEvent.class, (s, e) -> {
            urlDraft = e.text();
            return false;
        });

        loadButton = addComponent(new DLButton(PAD + fieldW + 3, ROW_Y, BTN_W, ROW_H));
        loadButton.text.set(TextUtils.text("Load"));
        loadButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            requestPreview();
            return false;
        });

        applyButton = addComponent(new DLButton(PAD + fieldW + BTN_W + 6, ROW_Y, BTN_W, ROW_H));
        applyButton.text.set(TextUtils.text("Apply"));
        applyButton.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            if (applyUrl(true)) {
                DLWindowManager mgr = getWindowManager();
                if (mgr != null) {
                    mgr.closeWindow(this);
                }
            }
            return false;
        });

        // Cache-thumbnail clicks. Click events carry window-local coordinates.
        addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            handleCanvasClick(e.mouseX(), e.mouseY());
            return false;
        });

        RequestCachePayload.request();
        refreshTabs();
    }

    private void refreshTabs() {
        if (urlField != null) {
            urlField.visible.set(!tabCache);
        }
        if (loadButton != null) {
            loadButton.visible.set(!tabCache);
        }
        if (applyButton != null) {
            applyButton.visible.set(!tabCache);
        }
    }

    // ── URL handling ───────────────────────────────────────────────────

    private void requestPreview() {
        String u = urlDraft == null ? "" : urlDraft.trim();
        if (u.isEmpty()) {
            status = TextUtils.text("Enter an image URL first");
            return;
        }
        if (!ImageUrlPolicy.isPlausibleImageUrl(u)) {
            status = TextUtils.text("Invalid URL - must be http(s) with a host");
            return;
        }
        urlDraft = u;
        if (bePos == null) {
            // No display to address, so there is nothing to ask the server for.
            status = TextUtils.text("URL set - no live preview for this window");
            return;
        }
        previewRequestedAt = System.currentTimeMillis();
        previewPending = true;
        status = TextUtils.text("Requesting from server...");
        FetchImagePayload.request(bePos, u);
    }

    /**
     * Writes the edited URL into the settings object the caller will persist.
     * Returns false when validation rejects the input.
     */
    private boolean applyUrl(boolean validate) {
        String u = urlDraft == null ? "" : urlDraft.trim();
        if (validate && !u.isEmpty() && !ImageUrlPolicy.isPlausibleImageUrl(u)) {
            status = TextUtils.text("Invalid URL - must be http(s) with a host");
            return false;
        }
        if (settings != null) {
            settings.setImageUrl(u);
        }
        if (onApplied != null) {
            onApplied.run();
        }
        return true;
    }

    private void showPreview(byte[] data) {
        previewRequestedAt = 0;
        status = TextUtils.text("Decoding...");
        if (preview == null) {
            preview = new DynamicTextureHolder(CANVAS_W, CANVAS_H);
        }
        preview.loadBytes(data,
            () -> {
                previewPending = false;
                status = TextUtils.text("Image OK - server delivered "
                    + preview.getWidth() + "x" + preview.getHeight() + " px");
            },
            () -> {
                previewPending = false;
                status = TextUtils.text("Preview failed (image could not be decoded)");
            });
    }

    // ── Cache listing ─────────────────────────────────────────────────

    private void setCacheData(List<String> raw) {
        releaseThumbs();
        for (String s : raw) {
            String[] p = s.split("\t", -1);
            if (p.length < 5) {
                continue;
            }
            ResourceLocation tex = null;
            DynamicTexture dt = null;
            NativeImage ni = null;
            int tw = 0;
            int th = 0;
            try {
                int w = Integer.parseInt(p[2]);
                int h = Integer.parseInt(p[3]);
                if (!p[4].isEmpty()) {
                    byte[] b = Base64.getDecoder().decode(p[4]);
                    ni = NativeImage.read(new ByteArrayInputStream(b));
                    tw = ni.getWidth();
                    th = ni.getHeight();
                    dt = new DynamicTexture(ni);
                    tex = ResourceLocation.fromNamespaceAndPath(
                        CRNDisplayNextMod.MOD_ID, "gfx_thumb/" + (thumbIdx++));
                    Minecraft.getInstance().getTextureManager().register(tex, dt);
                }
                cachedEntries.add(new Cached(p[0], p[1], w, h, tex, tw, th));
            } catch (Exception e) {
                // One malformed entry must not abort the whole listing.
                if (dt != null) {
                    dt.close();
                } else if (ni != null) {
                    ni.close();
                }
            }
        }
    }

    private static int thumbSize() {
        return (CANVAS_W - 4 - (COLS - 1) * 3) / COLS;
    }

    private static int thumbX(int i) {
        return CANVAS_X + 2 + (i % COLS) * (thumbSize() + 3);
    }

    private static int thumbY(int i) {
        return CANVAS_Y + 2 + (i / COLS) * (thumbSize() + 16);
    }

    /** {@code mouseX}/{@code mouseY} arrive in window-local coordinates. */
    private void handleCanvasClick(double mouseX, double mouseY) {
        if (!tabCache) {
            return;
        }
        int ts = thumbSize();
        for (int i = 0; i < Math.min(cachedEntries.size(), MAX_THUMBS); i++) {
            int tx = thumbX(i);
            int ty = thumbY(i);
            if (mouseX >= tx && mouseX <= tx + ts && mouseY >= ty && mouseY <= ty + ts + 14) {
                Cached c = cachedEntries.get(i);
                tabCache = false;
                urlDraft = c.url();
                if (urlField != null) {
                    urlField.text.set(urlDraft);
                }
                refreshTabs();
                requestPreview();
                return;
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────

    @Override
    public Rectangle getRenderBounds() {
        return Rectangle.withSize(0, 0, 1000, 1000);
    }

    @Override
    public void renderMainLayer(DLGuiGraphics graphics, double mouseX, double mouseY, Rectangle renderBounds) {
        GuiUtils.fill(graphics, 0, 0, WIN_W, WIN_H, WINDOW_BG);
        GuiUtils.drawString(graphics, graphics.defaultFont(), PAD, TITLE_Y,
            TextUtils.text("Custom Image Display"), DLColor.WHITE, ETextAlignment.LEFT, false);

        // Canvas frame + background.
        GuiUtils.fill(graphics, CANVAS_X - 1, CANVAS_Y - 1, CANVAS_W + 2, CANVAS_H + 2, BORDER);
        GuiUtils.fill(graphics, CANVAS_X, CANVAS_Y, CANVAS_W, CANVAS_H, CANVAS_BG);

        if (tabCache) {
            renderCacheGrid(graphics);
        } else {
            renderPreview(graphics);
        }

        if (status != null) {
            GuiUtils.drawString(graphics, graphics.defaultFont(), PAD, STATUS_Y,
                status, TEXT_STATUS, ETextAlignment.LEFT, false);
        }
    }

    private void renderPreview(DLGuiGraphics graphics) {
        if (preview != null && preview.isReady()) {
            float s = Math.min((float) CANVAS_W / preview.getWidth(),
                               (float) CANVAS_H / preview.getHeight());
            int dw = Math.max(1, (int) (preview.getWidth() * s));
            int dh = Math.max(1, (int) (preview.getHeight() * s));
            // Explicit texture size: the 11-arg overload defaults to a 256x256 atlas,
            // which crops any texture that is not that size.
            GuiUtils.drawTexture(preview.getId(), graphics,
                CANVAS_X + (CANVAS_W - dw) / 2, CANVAS_Y + (CANVAS_H - dh) / 2, dw, dh,
                0, 0, preview.getWidth(), preview.getHeight(),
                TextureFillMode.STRETCH, preview.getWidth(), preview.getHeight());
        } else {
            GuiUtils.drawString(graphics, graphics.defaultFont(),
                CANVAS_X + CANVAS_W / 2, CANVAS_Y + CANVAS_H / 2,
                TextUtils.text(previewPending ? "Loading from server..." : "No preview yet"),
                TEXT_DIM, ETextAlignment.CENTER, false);
        }
    }

    private void renderCacheGrid(DLGuiGraphics graphics) {
        if (cachedEntries.isEmpty()) {
            GuiUtils.drawString(graphics, graphics.defaultFont(),
                CANVAS_X + CANVAS_W / 2, CANVAS_Y + CANVAS_H / 2,
                TextUtils.text("No cached images."), TEXT_DIM, ETextAlignment.CENTER, false);
            return;
        }
        int ts = thumbSize();
        for (int i = 0; i < Math.min(cachedEntries.size(), MAX_THUMBS); i++) {
            Cached c = cachedEntries.get(i);
            int tx = thumbX(i);
            int ty = thumbY(i);
            GuiUtils.fill(graphics, tx, ty, ts, ts, THUMB_BG);
            if (c.tex() != null && c.tw() > 0 && c.th() > 0) {
                // Sample the thumbnail's real size; the default 256x256 assumption
                // showed only the top-left quarter of each cached image.
                GuiUtils.drawTexture(c.tex(), graphics, tx + 1, ty + 1, ts - 2, ts - 2,
                    0, 0, c.tw(), c.th(), TextureFillMode.STRETCH, c.tw(), c.th());
            }
            GuiUtils.drawString(graphics, graphics.defaultFont(), tx + 2, ty + ts + 3,
                TextUtils.text(c.w() + "x" + c.h()), TEXT_STATUS, ETextAlignment.LEFT, false);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (previewRequestedAt > 0 && previewPending
                && System.currentTimeMillis() - previewRequestedAt > PREVIEW_TIMEOUT_MS) {
            previewRequestedAt = 0;
            previewPending = false;
            status = TextUtils.text("Server did not deliver the image - check the URL and the log");
        }
    }
}
