package com.molox.createimp.screen;

import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.network.SaveNetworkManagerDataPacket;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class NetworkManagerLabelEditScreen
        extends AbstractSimiContainerScreen<NetworkManagerLabelEditMenu> {

    private static final int GUI_WIDTH  = AllGuiTextures.STOCK_KEEPER_CATEGORY.getWidth();
    private static final int HEADER_H   = AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.getHeight();
    private static final int EDIT_H     = AllGuiTextures.STOCK_KEEPER_CATEGORY_EDIT.getHeight();
    private static final int FOOTER_H   = AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.getHeight();
    private static final int INV_H      = 76;
    private static final int INV_GAP    = 14;
    private static final int GUI_HEIGHT = HEADER_H + EDIT_H + FOOTER_H + INV_H + INV_GAP;

    private static final int EDITBOX_X = 47;
    private static final int EDITBOX_Y = 33;
    private static final int EDITBOX_W = 124;
    private static final int EDITBOX_H = 10;

    private static final int CONFIRM_X = 167;
    private static final int CONFIRM_Y = 64;

    private static final int PLAYER_INV_RENDER_X = 10;
    private static final int PLAYER_INV_RENDER_Y = 98;

    private static final int TITLE_Y = 4;
    private static final int EDITOR_Y_OFFSET = 0;

    private static final int EDITBOX_COLOR = 0xEEEEEE;
    private static final int TITLE_COLOR   = 0x3D3C48;

    private EditBox nameEditBox;
    private IconButton confirmButton;

    public NetworkManagerLabelEditScreen(
            NetworkManagerLabelEditMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    public int getGuiLeft() { return leftPos; }
    public int getGuiTop()  { return topPos; }

    @Override
    protected void init() {
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
        super.init();

        int editorTop = topPos + EDITOR_Y_OFFSET;

        confirmButton = new IconButton(
                leftPos + CONFIRM_X,
                editorTop + CONFIRM_Y,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onConfirm);
        addRenderableWidget(confirmButton);

        nameEditBox = new EditBox(
                font,
                leftPos + EDITBOX_X,
                editorTop + EDITBOX_Y,
                EDITBOX_W,
                EDITBOX_H,
                Component.empty()
        );
        nameEditBox.setTextColor(EDITBOX_COLOR);
        nameEditBox.setBordered(false);
        nameEditBox.setFocused(false);
        nameEditBox.setMaxLength(28);
        nameEditBox.setValue(menu.editingName);
        addRenderableWidget(nameEditBox);
    }

    private void onConfirm() {
        ItemStack icon = menu.ghostInventory.getStackInSlot(0).copy();
        if (!icon.isEmpty()) icon.setCount(1);

        String name = nameEditBox.getValue().isBlank()
                ? menu.editingName
                : nameEditBox.getValue();

        List<NetworkLabel> newLabels = new ArrayList<>(menu.existingLabels);
        NetworkLabel original = newLabels.get(menu.editingIndex);
        newLabels.set(menu.editingIndex,
                new NetworkLabel(name, icon, original.networkId()));

        PacketDistributor.sendToServer(
                new SaveNetworkManagerDataPacket(menu.hand, newLabels, true));
        onClose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        int editorTop = topPos + EDITOR_Y_OFFSET;
        int y = editorTop;
        AllGuiTextures.STOCK_KEEPER_CATEGORY_HEADER.render(graphics, leftPos, y);
        y += HEADER_H;
        AllGuiTextures.STOCK_KEEPER_CATEGORY_EDIT.render(graphics, leftPos, y);
        y += EDIT_H;
        AllGuiTextures.STOCK_KEEPER_CATEGORY_FOOTER.render(graphics, leftPos, y);

        renderPlayerInventory(graphics, leftPos + PLAYER_INV_RENDER_X, topPos + PLAYER_INV_RENDER_Y);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        boolean onIconSlot = isMouseOverIconSlot(mouseX, mouseY);
        var savedSlot = hoveredSlot;
        if (onIconSlot) hoveredSlot = null;

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);

        if (onIconSlot) hoveredSlot = savedSlot;

        int editorTop = topPos + EDITOR_Y_OFFSET;

        var titleText = Component.translatable("createimp.gui.network_manager.edit_label_title")
                .getVisualOrderText();
        graphics.drawString(font, titleText,
                (int)(leftPos + GUI_WIDTH / 2f - font.width(titleText) / 2f),
                editorTop + TITLE_Y,
                TITLE_COLOR, false);

        if (nameEditBox != null && nameEditBox.isHovered() && !nameEditBox.isFocused()) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("create.gui.stock_ticker.category_name")
                            .withColor(0x5B6EE1),
                    Component.translatable("create.gui.schedule.lmb_edit")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .withStyle(ChatFormatting.ITALIC)
            ), mouseX, mouseY);
        }
    }

    private boolean isMouseOverIconSlot(int mouseX, int mouseY) {
        int slotScreenX = leftPos + NetworkManagerLabelEditMenu.ICON_SLOT_X;
        int slotScreenY = topPos  + NetworkManagerLabelEditMenu.ICON_SLOT_Y;
        return mouseX >= slotScreenX && mouseX < slotScreenX + 16
                && mouseY >= slotScreenY && mouseY < slotScreenY + 16;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        if ((keyCode == 257 || keyCode == 335) && getFocused() instanceof EditBox) {
            onConfirm(); return true;
        }
        if (getFocused() == null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirmButton != null && confirmButton.isMouseOver(mouseX, mouseY)) {
            onConfirm();
            return true;
        }
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        if (nameEditBox != null && nameEditBox.isMouseOver(mouseX, mouseY) && button == 0) {
            nameEditBox.moveCursorToEnd(false);
            nameEditBox.setHighlightPos(0);
        }
        return result;
    }
}