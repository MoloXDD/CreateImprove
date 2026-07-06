package com.molox.createimp.mixin;

import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = StockTickerBlockEntity.class, remap = false)
public abstract class MixinStockTickerBlockEntity {

    @ModifyVariable(method = "broadcastPackageRequest", at = @At("HEAD"), argsOnly = true)
    private PackageOrderWithCrafts createimp$stripTemplateOrders(PackageOrderWithCrafts order) {
        List<BigItemStack> stacks = order.orderedStacks().stacks();
        boolean hasToken = false;
        for (BigItemStack entry : stacks) {
            if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                hasToken = true;
                break;
            }
        }
        if (!hasToken) {
            return order;
        }
        List<BigItemStack> filtered = new ArrayList<>();
        for (BigItemStack entry : stacks) {
            if (!TemplateOrderTokenHelper.isToken(entry.stack)) {
                filtered.add(entry);
            }
        }
        return new PackageOrderWithCrafts(new PackageOrder(filtered), order.orderedCrafts());
    }
}