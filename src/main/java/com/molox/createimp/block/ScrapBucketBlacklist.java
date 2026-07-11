package com.molox.createimp.block;

import com.molox.createimp.CreateImp;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public final class ScrapBucketBlacklist {

    private ScrapBucketBlacklist() {
    }

    public static boolean isBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        List<String> blacklist = CreateImp.getConfig().scrapBucket.blacklistedItems;
        if (blacklist.isEmpty()) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String raw : blacklist) {
            ResourceLocation parsed = ResourceLocation.tryParse(raw);
            if (parsed != null && parsed.equals(itemId)) return true;
        }
        return false;
    }

    public static boolean isBlacklisted(FluidStack stack) {
        if (stack.isEmpty()) return false;
        List<String> blacklist = CreateImp.getConfig().scrapBucket.blacklistedFluids;
        if (blacklist.isEmpty()) return false;
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        for (String raw : blacklist) {
            ResourceLocation parsed = ResourceLocation.tryParse(raw);
            if (parsed != null && parsed.equals(fluidId)) return true;
        }
        return false;
    }
}