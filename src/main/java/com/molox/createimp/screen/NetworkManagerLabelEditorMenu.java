package com.molox.createimp.screen;

import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.registry.ModMenuTypes;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.List;

public class NetworkManagerLabelEditorMenu extends GhostItemMenu<ItemStack> {

    public static final int ICON_SLOT_X = 16;
    public static final int ICON_SLOT_Y = 29;

    public static final int PLAYER_INV_SLOT_X = 18;
    public static final int PLAYER_INV_SLOT_Y = 116;

    // 图标槽在 slots 列表中的索引（玩家背包 36 个槽之后）
    public static final int ICON_SLOT_INDEX = 36;

    public final InteractionHand hand;
    public final List<NetworkLabel> existingLabels;

    public NetworkManagerLabelEditorMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(ModMenuTypes.NETWORK_MANAGER_LABEL_EDITOR.get(), id, inv,
                buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
    }

    public NetworkManagerLabelEditorMenu(MenuType<?> type, int id, Inventory inv,
                                         InteractionHand hand, List<NetworkLabel> existingLabels) {
        super(type, id, inv, ItemStack.EMPTY);
        this.hand = hand;
        this.existingLabels = existingLabels;
    }

    @Override
    protected ItemStack createOnClient(RegistryFriendlyByteBuf buf) {
        return ItemStack.EMPTY;
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(1);
    }

    @Override
    protected void initAndReadInventory(ItemStack ignored) {
        ghostInventory = createGhostInventory();
        var stockLink = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "stock_link"));
        ghostInventory.setStackInSlot(0, new ItemStack(stockLink));
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(PLAYER_INV_SLOT_X, PLAYER_INV_SLOT_Y);
        addSlot(new SlotItemHandler(ghostInventory, 0, ICON_SLOT_X, ICON_SLOT_Y));
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        // 图标槽空手点击时不清空，保持原有图标
        if (slotId == ICON_SLOT_INDEX && getCarried().isEmpty()) {
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    protected void saveData(ItemStack ignored) {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean allowRepeats() {
        return true;
    }
}