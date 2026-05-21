package com.hybrizat.crndisplaynext.display;

import com.hybrizat.crndisplaynext.display.ber.BERJREPlatformLED;
import com.hybrizat.crndisplaynext.display.settings.JREPlatformLEDSettings;
import de.mrjulsen.crn.block.properties.EDisplayType;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry.DisplayProperties;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry.DisplayTypeResourceKey;

/**
 * Registers our custom display types into CRN's registry.
 *
 * Call ModDisplayTypesExt.init() during FMLCommonSetupEvent (server+client)
 * OR during FMLClientSetupEvent (client only — safe since display rendering
 * only runs client-side).
 *
 * DisplayTypeResourceKey is used by CRN's GUI picker to show our types
 * alongside the built-in ones in the correct category.
 *
 * Format: AdvancedDisplaysRegistry.register(
 *   EDisplayType category,    ← which category in the GUI dropdown
 *   String variantName,       ← unique name within that category
 *   Supplier<IDisplaySettings> settingsFactory,
 *   Supplier<IBERRenderSubtype> rendererFactory,
 *   DisplayProperties props    ← singleLined, platformDisplayTrainsCount
 * )
 */
public final class ModDisplayTypesExt {

    // ── Station Platform Displays ──────────────────────────────────────────

    /**
     * JRE LED — single-line scrolling LED departure board.
     * Appears in the GUI under: Platform → JRE LED
     * singleLined = true: fits in a 1-block-tall display.
     */
    public static final DisplayTypeResourceKey JRE_PLATFORM_LED =
            AdvancedDisplaysRegistry.register(
                    EDisplayType.PLATFORM,
                    "jre_led",                          // unique variant name
                    JREPlatformLEDSettings::new,
                    BERJREPlatformLED::new,
                    new AdvancedDisplaysRegistry.DisplayProperties(
                            true,                           // singleLined
                            // 放弃使用容易引发隐式类型推导的 Lambda 表达式
                            // 改用显式的匿名内部类，将入参当做 Object 来看待，完全断绝编译器去追溯 AdvancedDisplayBlockEntity 继承链的念头
                            new java.util.function.Function<de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity, Integer>() {
                                @Override
                                public Integer apply(de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity be) {
                                    // 在这里将 be 视作纯粹的 Object
                                    Object safeBe = be;
                                    if (safeBe == null) return 0;

                                    // JRE LED 通常只需要拉取 1-2 趟车次（1 趟当前，1 趟次车）来循环滚动即可
                                    // 如果你希望它根据屏幕高度来，可以用反射：
                                    try {
                                        // 动态反射调用 getYSizeScaled()，绕过编译期的强类型检查
                                        return ((Number) safeBe.getClass().getMethod("getYSizeScaled").invoke(safeBe)).intValue();
                                    } catch (Exception e) {
                                        return 1; // 反射失败的保底值，拉取 1 趟车次
                                    }
                                }
                            }
                    )
            );

    // ── Passenger Information Displays ────────────────────────────────────
    // TODO Phase 3: JRE VIS, JRE LED (in-car)

    // ── Train Destination Displays ────────────────────────────────────────
    // TODO Phase 4: JRE Front, JRE Side Detailed, JRE Side Compact

    /**
     * Must be called during mod setup so the registry entries are populated
     * before the game tries to load block entity data or render displays.
     *
     * CRN calls ModDisplayTypes.init() in its own setup — we do the same.
     */
    public static void init() {
        // Reference the fields to force static initialization
        // (Java initializes static fields lazily; touching them forces it)
        DisplayTypeResourceKey[] keys = { JRE_PLATFORM_LED };


    }
}
