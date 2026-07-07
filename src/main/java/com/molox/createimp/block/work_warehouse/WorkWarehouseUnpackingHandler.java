package com.molox.createimp.block.work_warehouse;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum WorkWarehouseUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side,
                          List<ItemStack> items, @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        if (!(level.getBlockEntity(pos) instanceof WorkWarehouseBlockEntity be)) {
            return false;
        }

        WorkWarehouseItemStackHandler scratch = be.storage.copy();
        for (ItemStack item : items) {
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(scratch, item.copy(), false);
            if (!leftover.isEmpty()) {
                return false;
            }
        }

        if (simulate) {
            return true;
        }

        for (ItemStack item : items) {
            ItemHandlerHelper.insertItemStacked(be.storage, item.copy(), false);
        }

        return true;
    }
}