package com.molox.createimp.mixin;

import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(value = LogisticsManager.class, remap = false)
public abstract class MixinLogisticsManager {

    @ModifyVariable(method = "findPackagersForRequest", at = @At("HEAD"), argsOnly = true)
    private static PackageOrderWithCrafts createimp$stripTemplatesAndActivateWarehouse(
            PackageOrderWithCrafts order,
            UUID freqId,
            PackageOrderWithCrafts orderArgument,
            IdentifiedInventory ignoredHandler,
            String address) {
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

        WorkWarehouseBlockEntity warehouse = WorkWarehouseNetworkHelper.findAvailableWorkWarehouse(freqId);
        if (warehouse != null) {
            warehouse.activate(address);
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