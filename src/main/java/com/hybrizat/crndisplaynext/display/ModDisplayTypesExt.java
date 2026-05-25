package com.hybrizat.crndisplaynext.display;

import com.hybrizat.crndisplaynext.display.ber.BERJREPlatform;
import com.hybrizat.crndisplaynext.display.ber.BERJREStation;
import com.hybrizat.crndisplaynext.display.settings.JREPlatformSettings;
import com.hybrizat.crndisplaynext.display.settings.JREStationSettings;
import de.mrjulsen.crn.block.properties.EDisplayType;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry.DisplayProperties;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry.DisplayTypeResourceKey;

public final class ModDisplayTypesExt {

    private static boolean initialized = false;

    // Station concourse display (has platform column)
    public static DisplayTypeResourceKey JRE_STATION;
    // Platform edge display (no platform column)
    public static DisplayTypeResourceKey JRE_PLATFORM;

    public static void init() {
        if (initialized) return;
        initialized = true;

        JRE_STATION = AdvancedDisplaysRegistry.register(
            EDisplayType.PLATFORM,
            "jre_station",
            JREStationSettings::new,
            BERJREStation::new,
            new DisplayProperties(false, be -> be.getYSize() * 3 - 1)
        );

        JRE_PLATFORM = AdvancedDisplaysRegistry.register(
            EDisplayType.PLATFORM,
            "jre_platform",
            JREPlatformSettings::new,
            BERJREPlatform::new,
            new DisplayProperties(false, be -> be.getYSize() * 3 - 1)
        );
    }
}
