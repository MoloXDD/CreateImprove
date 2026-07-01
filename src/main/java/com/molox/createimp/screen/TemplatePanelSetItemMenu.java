package com.molox.createimp.screen;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.molox.createimp.registry.ModMenuTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class TemplatePanelSetItemMenu extends GhostItemMenu<TemplatePanelBehaviour> {

    public TemplatePanelSetItemMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(ModMenuTypes.TEMPLATE_PANEL_SET_ITEM.get(), id, inv, buf);
    }

    public TemplatePanelSetItemMenu(MenuType<?> type, int id, Inventory inv, TemplatePanelBehaviour contentHolder) {
        super(type, id, inv, contentHolder);
    }

    public TemplatePanelSetItemMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public static TemplatePanelSetItemMenu create(int id, Inventory inv, TemplatePanelBehaviour be) {
        return new TemplatePanelSetItemMenu(ModMenuTypes.TEMPLATE_PANEL_SET_ITEM.get(), id, inv, be);
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(1);
    }

    @Override
    protected boolean allowRepeats() {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected TemplatePanelBehaviour createOnClient(RegistryFriendlyByteBuf extraData) {
        TemplatePanelPosition pos = TemplatePanelPosition.STREAM_CODEC.decode(extraData);
        return TemplatePanelBehaviour.at((BlockAndTintGetter) Minecraft.getInstance().level, pos);
    }

    @Override
    protected void addSlots() {
        int playerX = 13;
        int playerY = 112;
        int slotX = 74;
        int slotY = 28;
        this.addPlayerSlots(playerX, playerY);
        this.addSlot(new SlotItemHandler((IItemHandler) this.ghostInventory, 0, slotX, slotY));
    }

    @Override
    protected void saveData(TemplatePanelBehaviour contentHolder) {
        if (!contentHolder.setFilter(this.ghostInventory.getStackInSlot(0))) {
            this.player.displayClientMessage(CreateLang.translateDirect("logistics.filter.invalid_item"), true);
            AllSoundEvents.DENY.playOnServer(this.player.level(), (Vec3i) this.player.blockPosition(), 1.0f, 1.0f);
            return;
        }
        this.player.level().playSound(null, contentHolder.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.25f, 0.1f);
    }
}