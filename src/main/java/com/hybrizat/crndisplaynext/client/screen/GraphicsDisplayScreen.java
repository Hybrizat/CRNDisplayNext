package com.hybrizat.crndisplaynext.client.screen;

import com.hybrizat.crndisplaynext.block.entity.GraphicsDisplayBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GraphicsDisplayScreen extends Screen {

    private final GraphicsDisplayBlockEntity be;
    private EditBox colorInput;

    public GraphicsDisplayScreen(GraphicsDisplayBlockEntity be) {
        super(Component.literal("Display Settings"));
        this.be = be;
    }

    @Override protected void init() {
        int cx = (width-180)/2, cy = (height-80)/2;
        colorInput = new EditBox(font,cx+80,cy+20,90,20,Component.literal("BG"));
        colorInput.setValue(String.format("%06X", be.getBgColor() & 0xFFFFFF));
        colorInput.setFilter(s -> s.matches("[0-9a-fA-F]{0,6}"));
        addRenderableWidget(colorInput);
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> {
            try { be.setBgColor(0xFF000000|Integer.parseInt(colorInput.getValue(),16)); }
            catch (Exception e) {}
            onClose();
        }).pos(cx+123, cy+55).size(52,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .pos(cx+5, cy+55).size(50,20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float p) {
        super.render(g,mx,my,p);
        int cx = (width-180)/2, cy = (height-80)/2;
        g.fill(cx,cy,cx+180,cy+80,0xCC222233);
        g.drawString(font,"Display Settings",cx+5,cy+4,0xFFFFFF);
        g.drawString(font,"BG Color:",cx+5,cy+23,0xAAAAAA);
        g.drawString(font,"Size: "+be.getDisplayWidth()+"\u00d7"+be.getDisplayHeight(),
            cx+5,cy+47,0x888888);
    }

    @Override public boolean isPauseScreen() { return false; }
}
