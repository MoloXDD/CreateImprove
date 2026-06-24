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

public class NetworkManagerLabelEditMenu extends GhostItemMenu<ItemStack> {

    public static final int ICON_SLOT_X = 16;
    public static final int ICON_SLOT_Y = 29;

    public static final int PLAYER_INV_SLOT_X = 18;
    public static final int PLAYER_INV_SLOT_Y = 116;

    public static final int ICON_SLOT_INDEX = 36;

    // 静态临时变量，解决父类构造期间回调 initAndReadInventory 时子类字段尚未赋值的问题
    private static ItemStack pendingIcon = null;

    public final InteractionHand hand;
    public final List<NetworkLabel> existingLabels;
    public final int editingIndex;
    public final ItemStack editingIcon;
    public final String editingName;

    public NetworkManagerLabelEditMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(ModMenuTypes.NETWORK_MANAGER_LABEL_EDIT.get(), id, inv,
                buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                buf.readInt(),
                ItemStack.STREAM_CODEC.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf));
    }

    public NetworkManagerLabelEditMenu(MenuType<?> type, int id, Inventory inv,
                                       InteractionHand hand, List<NetworkLabel> existingLabels,
                                       int editingIndex, ItemStack editingIcon, String editingName) {
        super(type, id, inv, ItemStack.EMPTY);
        this.hand = hand;
        this.existingLabels = existingLabels;
        this.editingIndex = editingIndex;
        this.editingIcon = editingIcon;
        this.editingName = editingName;
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
        ItemStack icon = pendingIcon;
        pendingIcon = null;
        if (icon != null && !icon.isEmpty()) {
            ghostInventory.setStackInSlot(0, icon);
        } else {
            var stockLink = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("create", "stock_link"));
            ghostInventory.setStackInSlot(0, new ItemStack(stockLink));
        }
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(PLAYER_INV_SLOT_X, PLAYER_INV_SLOT_Y);
        addSlot(new SlotItemHandler(ghostInventory, 0, ICON_SLOT_X, ICON_SLOT_Y));
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
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

    public static void setPendingIcon(ItemStack icon) {
        pendingIcon = icon;
    }
}