package com.molox.createimp.mixin;

import com.molox.createimp.item.TemplateOrderSummaryHelper;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.LogisticalStockRequestPacket;
import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LogisticalStockRequestPacket.class, remap = false)
public abstract class MixinLogisticalStockRequestPacket {

    @Inject(method = "applySettings", at = @At("HEAD"), cancellable = true)
    private void createimp$injectTemplateItems(ServerPlayer player, StockCheckingBlockEntity be, CallbackInfo ci) {
        if (!(be instanceof StockTickerBlockEntity stbe)) {
            return;
        }
        if (stbe.behaviour == null || stbe.behaviour.freqId == null) {
            return;
        }
        InventorySummary summary = TemplateOrderSummaryHelper.augment(stbe.getRecentSummary(), stbe.behaviour.freqId);
        summary.divideAndSendTo(player, stbe.getBlockPos());
        ci.cancel();
    }
}