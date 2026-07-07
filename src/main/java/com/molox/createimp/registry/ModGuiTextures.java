package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public enum ModGuiTextures {

    TEMPLATE_PANEL_RECIPE(32, 0, 192, 96),
    TEMPLATE_PANEL_BOTTOM(32, 176, 200, 64),
    WORK_WAREHOUSE_ADDRESS_INPUT("ware_house_address_input", 16, 16, 232, 95, 256, 112);

    public final ResourceLocation location;
    private final int width;
    private final int height;
    private final int startX;
    private final int startY;
    private final int textureWidth;
    private final int textureHeight;

    ModGuiTextures(int startX, int startY, int width, int height) {
        this("template_factory_gauge", startX, startY, width, height, 256, 256);
    }

    ModGuiTextures(String textureFile, int startX, int startY, int width, int height,
                   int textureWidth, int textureHeight) {
        this.location = ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/" + textureFile + ".png");
        this.width = width;
        this.height = height;
        this.startX = startX;
        this.startY = startY;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(this.location, x, y, 0,
                (float) this.startX, (float) this.startY,
                this.width, this.height,
                this.textureWidth, this.textureHeight);
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