package com.molox.createimp.mixin;

import com.molox.createimp.item.TemplateOrderSummaryHelper;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StockTickerInteractionHandler.class, remap = false)
public abstract class MixinStockTickerInteractionHandler {

    @Redirect(method = "interactWithLogisticsManagerAt", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/StockTickerBlockEntity;getRecentSummary()Lcom/simibubi/create/content/logistics/packager/InventorySummary;"))
    private static InventorySummary createimp$augmentRecentSummary(StockTickerBlockEntity stbe) {
        InventorySummary summary = stbe.getRecentSummary();
        if (stbe.behaviour != null && stbe.behaviour.freqId != null) {
            return TemplateOrderSummaryHelper.augment(summary, stbe.behaviour.freqId);
        }
        return summary;
    }
}