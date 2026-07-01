package com.molox.createimp.screen;

import com.molox.createimp.registry.ModItems;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class TemplatePanelSetItemScreen extends AbstractSimiContainerScreen<TemplatePanelSetItemMenu> {

    private IconButton confirmButton;
    private List<Rect2i> extraAreas = Collections.emptyList();

    public TemplatePanelSetItemScreen(TemplatePanelSetItemMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Override
    protected void init() {
        int bgHeight = AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getHeight();
        int bgWidth = AllGuiTextures.FACTORY_GAUGE_SET_ITEM.getWidth();
        this.setWindowSize(bgWidth, bgHeight + AllGuiTextures.PLAYER_INVENTORY.getHeight());
        super.init();
        this.clearWidgets();
        int x = this.getGuiLeft();
        int y = this.getGuiTop();
        this.confirmButton = new IconButton(x + bgWidth - 40, y + bgHeight - 25, AllIcons.I_CONFIRM);
        this.confirmButton.withCallback(() -> this.minecraft.player.closeContainer());
        this.addRenderableWidget(this.confirmButton);
        this.extraAreas = List.of(new Rect2i(x + bgWidth, y + bgHeight - 30, 40, 20));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.getGuiLeft();
        int y = this.getGuiTop();
        AllGuiTextures.FACTORY_GAUGE_SET_ITEM.render(graphics, x - 5, y);
        this.renderPlayerInventory(graphics, x + 5, y + 94);
        ItemStack stack = new ItemStack(ModItems.TEMPLATE_PANEL.get());
        MutableComponent title = CreateLang.translate("gui.factory_panel.place_item_to_monitor").component();
        graphics.drawString(this.font, (Component) title, x + this.imageWidth / 2 - this.font.width((FormattedText) title) / 2 - 5, y + 4, 4013128, false);
        GuiGameElement.of(stack).scale(3.0).render(graphics, x + 180, y + 48);
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return this.extraAreas;
    }
}