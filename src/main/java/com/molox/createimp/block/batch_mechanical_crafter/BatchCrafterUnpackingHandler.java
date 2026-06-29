package com.molox.createimp.block.batch_mechanical_crafter;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum BatchCrafterUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side,
                          List<ItemStack> items, @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        if (!PackageOrderWithCrafts.hasCraftingInformation(orderContext)) {
            return DEFAULT.unpack(level, pos, state, side, items, null, simulate);
        }
        List<BigItemStack> craftingContext = orderContext.getCraftingInformation();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BatchMechanicalCrafterBlockEntity crafter)) {
            return false;
        }
        BatchConnectedInputHandler.ConnectedInput input = crafter.getInput();
        List<BatchMechanicalCrafterBlockEntity.Inventory> inventories = input.getInventories(level, pos);
        if (inventories.isEmpty()) {
            return false;
        }
        int max = Math.min(inventories.size(), craftingContext.size());
        outer:
        for (int i = 0; i < max; i++) {
            BigItemStack targetStack = craftingContext.get(i);
            if (targetStack.stack.isEmpty()) continue;
            BatchMechanicalCrafterBlockEntity.Inventory inventory = inventories.get(i);
            if (!inventory.getStackInSlot(0).isEmpty()) continue;
            for (ItemStack stack : items) {
                if (!ItemStack.isSameItemSameComponents(stack, targetStack.stack)) continue;
                ItemStack toInsert = stack.copyWithCount(1);
                if (!inventory.insertItem(0, toInsert, simulate).isEmpty()) continue;
                stack.shrink(1);
                continue outer;
            }
        }
        for (ItemStack item : items) {
            if (!item.isEmpty()) return false;
        }
        if (!simulate) {
            crafter.checkCompletedRecipe(true);
        }
        return true;
    }
}