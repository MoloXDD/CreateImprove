package com.molox.createimp.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.molox.createimp.compat.MobilePackagesRequestContext;
import com.molox.createimp.item.TemplateOrderSummaryHelper;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import de.theidler.create_mobile_packages.items.portable_stock_ticker.LogisticallyLinkedItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey;
import ru.zznty.create_factory_abstractions.generic.support.GenericOrder;

import java.util.UUID;

/** Optional: register only when create_mobile_packages is loaded. */
@Mixin(targets = "de.theidler.create_mobile_packages.items.portable_stock_ticker.StockCheckingItem", remap = false)
public abstract class MixinMobilePackagesStockCheckingItem {
    @Redirect(method = "getAccurateSummary", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagerLink/LogisticsManager;getSummaryOfNetwork(Ljava/util/UUID;Z)Lcom/simibubi/create/content/logistics/packager/InventorySummary;"))
    private static InventorySummary createimp$includeTemplates(UUID network, boolean accurate) {
        // Augment a copy before GenericInventorySummary.of wraps it. Never pollute the shared
        // LogisticsManager cache with virtual stock or change non-Mobile inventory queries.
        return TemplateOrderSummaryHelper.augment(LogisticsManager.getSummaryOfNetwork(network, accurate), network);
    }

    @WrapMethod(method = "broadcastPackageRequest")
    private boolean createimp$dispatchTemplatesAndScopeAddress(ItemStack stack,
            LogisticallyLinkedBehaviour.RequestType type, GenericOrder order,
            IdentifiedInventory ignoredHandler, String address, Operation<Boolean> original) {
        UUID network = LogisticallyLinkedItem.networkFromStack(stack);
        try (MobilePackagesRequestContext ignored = MobilePackagesRequestContext.enter(network, address)) {
            // CFLBridge recognizes package-resource keys, not template components. Both item
            // and fluid template tokens MUST go through our Create LogisticsManager mixin,
            // which strips the virtual stock and activates work warehouses exactly once.
            boolean hasTemplate = order.stacks().stream().anyMatch(entry ->
                    entry.key() instanceof ItemKey key && TemplateOrderTokenHelper.isToken(key.stack()));
            if (network != null && hasTemplate) {
                return LogisticsManager.broadcastPackageRequest(network, type, order.asCrafting(), ignoredHandler, address);
            }
            // Retain upstream's package-resource/real-fluid dispatch. The scope covers BOTH
            // upstream branches and is restored even if dispatch throws or requests nest.
            return original.call(stack, type, order, ignoredHandler, address);
        }
    }
}
