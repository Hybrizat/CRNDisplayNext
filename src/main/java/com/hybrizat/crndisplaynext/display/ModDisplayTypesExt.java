package com.hybrizat.crndisplaynext.display;

import com.hybrizat.crndisplaynext.display.ber.*;
import com.hybrizat.crndisplaynext.display.settings.*;
import de.mrjulsen.crn.block.properties.EDisplayType;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry.DisplayProperties;
import de.mrjulsen.crn.client.AdvancedDisplaysRegistry.DisplayTypeResourceKey;

public final class ModDisplayTypesExt {

    private static boolean initialized = false;

    // Platform displays
    public static DisplayTypeResourceKey JRE_STATION;
    public static DisplayTypeResourceKey JRE_PLATFORM;

    // Train destination displays
    public static DisplayTypeResourceKey JRE_FRONT;
    public static DisplayTypeResourceKey JRE_SIDE;

    // Passenger information
    public static DisplayTypeResourceKey JRE_VIS;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // Station concourse display (has platform column)
        JRE_STATION = AdvancedDisplaysRegistry.register(
            EDisplayType.PLATFORM,
            "jre_station",
            JREStationSettings::new,
            BERJREStation::new,
            new DisplayProperties(false, be -> be.getYSize() * 3 - 1)
        );

        // Platform edge display (no platform column, optional direction arrow)
        JRE_PLATFORM = AdvancedDisplaysRegistry.register(
            EDisplayType.PLATFORM,
            "jre_platform",
            JREPlatformSettings::new,
            BERJREPlatform::new,
            new DisplayProperties(false, be -> be.getYSize() * 3 - 1)
        );

        // Front destination display (A: 種別, B: 行先 or 線路)
        JRE_FRONT = AdvancedDisplaysRegistry.register(
            EDisplayType.TRAIN_DESTINATION,
            "jre_front",
            JREFrontSettings::new,
            BERJREFront::new,
            new DisplayProperties(true, null)
        );

        // Side destination display (3 modes: NEXT_STOP / DEST / LINE)
        JRE_SIDE = AdvancedDisplaysRegistry.register(
            EDisplayType.TRAIN_DESTINATION,
            "jre_side",
            JRESideSettings::new,
            BERJRESide::new,
            new DisplayProperties(true, null)
        );

        // Passenger VIS (LCD schedule + category header)
        JRE_VIS = AdvancedDisplaysRegistry.register(
            EDisplayType.PASSENGER_INFORMATION,
            "jre_vis",
            JREVISSettings::new,
            BERJREPassengerVIS::new,
            new DisplayProperties(false, null)
        );
    }
}
