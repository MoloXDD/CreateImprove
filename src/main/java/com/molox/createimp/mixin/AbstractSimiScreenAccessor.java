package com.molox.createimp.mixin;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AbstractSimiScreen.class, remap = false)
public interface AbstractSimiScreenAccessor {

    @Accessor("guiLeft")
    int createimp$getGuiLeft();

    @Accessor("guiTop")
    int createimp$getGuiTop();

    @Accessor("windowHeight")
    int createimp$getWindowHeight();

    @Invoker("addRenderableWidgets")
    <W extends GuiEventListener & Renderable> void createimp$addRenderableWidgets(W... widgets);
}