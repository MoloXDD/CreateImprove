package com.molox.createimp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class ClientCreativeSyncHelper {

    public static void syncCreativeModeItemAdd(ItemStack stack, int inventoryMenuSlot) {
        Minecraft.getInstance().gameMode.handleCreativeModeItemAdd(stack, inventoryMenuSlot);
    }
}