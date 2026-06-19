package com.molox.createimp.screen;

import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketBlockEntity;
import com.molox.createimp.network.UpdateBrassScrapBucketAmountPacket;
import com.molox.createimp.registry.ModMenuTypes;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class BrassScrapBucketMenu extends GhostItemMenu<ItemStack> {

    public static final int FILTER_ICON_SLOT_X = 24;
    public static final int FILTER_ICON_SLOT_Y = 24;

    public static final int PLAYER_INV_SLOT_X = 7;
    public static final int PLAYER_INV_SLOT_Y = 101;

    public static final int FILTER_ICON_SLOT_INDEX = 36;

    public final BlockPos pos;
    public final int attachType;
    public final int keepAmount;
    public final boolean keepInStacks;
    public final int maxItems;
    public final int maxStacks;
    public final int currentAmount;
    public final int currentStacks;

    public BrassScrapBucketMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(ModMenuTypes.BRASS_SCRAP_BUCKET.get(), id, inv,
                BlockPos.STREAM_CODEC.decode(buf),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public BrassScrapBucketMenu(MenuType<?> type, int id, Inventory inv,
                                BlockPos pos, int attachType, int keepAmount, boolean keepInStacks,
                                int maxItems, int maxStacks, int currentAmount, int currentStacks,
                                ItemStack initialFilterIcon) {
        super(type, id, inv, ItemStack.EMPTY);
        this.pos = pos;
        this.attachType = attachType;
        this.keepAmount = keepAmount;
        this.keepInStacks = keepInStacks;
        this.maxItems = maxItems;
        this.maxStacks = maxStacks;
        this.currentAmount = currentAmount;
        this.currentStacks = currentStacks;
        ghostInventory.setStackInSlot(0, initialFilterIcon.copy());
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
    }

    @Override
    protected void addSlots() {
        addPlayerSlots(PLAYER_INV_SLOT_X, PLAYER_INV_SLOT_Y);
        addSlot(new SlotItemHandler(ghostInventory, 0, FILTER_ICON_SLOT_X, FILTER_ICON_SLOT_Y));
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId != FILTER_ICON_SLOT_INDEX) {
            super.clicked(slotId, dragType, clickType, player);
            return;
        }

        ItemStack carried = getCarried();
        ItemStack current = ghostInventory.getStackInSlot(0);
        boolean carriedIsFilter = !carried.isEmpty() && carried.getItem() instanceof FilterItem;
        boolean currentIsFilter = !current.isEmpty() && current.getItem() instanceof FilterItem;

        if (carried.isEmpty()) {
            if (currentIsFilter) {
                ItemStack toReturn = current.copy();
                ghostInventory.setStackInSlot(0, ItemStack.EMPTY);
                player.getInventory().placeItemBackInInventory(toReturn);
            } else {
                ghostInventory.setStackInSlot(0, ItemStack.EMPTY);
            }
        } else if (carriedIsFilter) {
            if (currentIsFilter) {
                ItemStack toReturn = current.copy();
                ItemStack toPlace = carried.copy();
                toPlace.setCount(1);
                ghostInventory.setStackInSlot(0, toPlace);
                carried.shrink(1);
                setCarried(carried);
                player.getInventory().placeItemBackInInventory(toReturn);
            } else {
                ItemStack toPlace = carried.copy();
                toPlace.setCount(1);
                ghostInventory.setStackInSlot(0, toPlace);
                carried.shrink(1);
                setCarried(carried);
            }
        } else {
            if (currentIsFilter) {
                ItemStack toReturn = current.copy();
                player.getInventory().placeItemBackInInventory(toReturn);
            }
            ItemStack copy = carried.copy();
            copy.setCount(1);
            ghostInventory.setStackInSlot(0, copy);
        }

        syncFilterToBlockEntity(player);
    }

    private void syncFilterToBlockEntity(Player player) {
        if (player.level().isClientSide()) return;
        if (!(player.level().getBlockEntity(pos) instanceof BrassScrapBucketBlockEntity be)) return;

        ItemStack newFilter = ghostInventory.getStackInSlot(0).copy();
        be.filterIcon = newFilter;
        be.filtering.setFilter(newFilter.copy());
        be.setChanged();
        be.sendData();

        int newAmount = 0;
        int newStacks = 0;
        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM) {
            newAmount = newFilter.isEmpty() ? be.getAboveCurrentItems() : be.getFilteredCurrentItems();
            newStacks = newFilter.isEmpty() ? be.getAboveCurrentStacks() : be.getFilteredCurrentStacks();
        } else if (attachType == BrassScrapBucketBlockEntity.ATTACH_FLUID) {
            newAmount = newFilter.isEmpty() ? be.getAboveCurrentFluids() : be.getFilteredCurrentFluids();
        }

        PacketDistributor.sendToPlayer((ServerPlayer) player,
                new UpdateBrassScrapBucketAmountPacket(newAmount, newStacks));
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