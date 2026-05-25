package com.hybrizat.crndisplaynext.display.settings;

import de.mrjulsen.crn.block.display.properties.IDisplaySettings;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;
import de.mrjulsen.mcdragonlib.util.DLColor;

/**
 * Settings for the JRE LED Station display (station concourse / ticket gate level).
 * Shows: [TrainText] [Time] [Destination] [Platform]
 * Platform column is visible. No direction arrow.
 */
public class JREStationSettings extends AbstractJRESettings {

    private static final DLColor LED_AMBER = DLColor.fromInt(0xFFFF8C00);
    private static final DLColor LED_BACK  = DLColor.fromInt(0xFF111111);

    public JREStationSettings() {
        super(LED_AMBER, LED_BACK);
    }

    @Override
    public void buildGui(GuiBuilderContext context) {
        buildSharedGui(context);
    }

    @Override
    public void onChangeSettings(IDisplaySettings old) {
        copySharedSettings(old);
    }
}
