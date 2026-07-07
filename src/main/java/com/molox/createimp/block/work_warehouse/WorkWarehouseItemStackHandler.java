package com.molox.createimp.block.work_warehouse;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class WorkWarehouseItemStackHandler extends ItemStackHandler {

    public static final int SLOT_COUNT = 100;

    private final WorkWarehouseBlockEntity blockEntity;

    public WorkWarehouseItemStackHandler(WorkWarehouseBlockEntity blockEntity) {
        super(SLOT_COUNT);
        this.blockEntity = blockEntity;
    }

    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        return stack.getMaxStackSize() > 1 ? Integer.MAX_VALUE : stack.getMaxStackSize();
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }

    public WorkWarehouseItemStackHandler copy() {
        WorkWarehouseItemStackHandler clone = new WorkWarehouseItemStackHandler(null);
        for (int i = 0; i < getSlots(); i++) {
            clone.setStackInSlot(i, getStackInSlot(i).copy());
        }
        return clone;
    }
}