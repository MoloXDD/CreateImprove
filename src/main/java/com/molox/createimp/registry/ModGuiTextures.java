package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public enum ModGuiTextures {

    TEMPLATE_PANEL_RECIPE(32, 0, 192, 96),
    TEMPLATE_PANEL_BOTTOM(32, 176, 200, 64);

    public final ResourceLocation location;
    private final int width;
    private final int height;
    private final int startX;
    private final int startY;

    ModGuiTextures(int startX, int startY, int width, int height) {
        this.location = ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/template_factory_gauge.png");
        this.width = width;
        this.height = height;
        this.startX = startX;
        this.startY = startY;
    }

    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(this.location, x, y, this.startX, this.startY, this.width, this.height);
    }

    public int getStartX() {
        return this.startX;
    }

    public int getStartY() {
        return this.startY;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }
}