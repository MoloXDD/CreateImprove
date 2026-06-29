package com.molox.createimp.block.batch_mechanical_crafter;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import java.util.List;

public class BatchCraftingInput extends CraftingInput {

    public BatchCraftingInput(int width, int height, List<ItemStack> items) {
        super(width, height, items);
    }

    public static BatchCraftingInput of(int width, int height, List<ItemStack> items) {
        return new BatchCraftingInput(width, height, items);
    }
}