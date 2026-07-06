package com.molox.createimp.mixin;

import com.molox.createimp.item.TemplateOrderSummaryHelper;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = StockTickerInteractionHandler.class, remap = false)
public abstract class MixinStockTickerInteractionHandler {

    @Redirect(method = "interactWithLogisticsManagerAt", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/InventorySummary;divideAndSendTo(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)V"))
    private static void createimp$redirectInitialSummary(InventorySummary summary, ServerPlayer player, BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        InventorySummary augmented = summary;
        if (be instanceof StockTickerBlockEntity stbe && stbe.behaviour != null && stbe.behaviour.freqId != null) {
            augmented = TemplateOrderSummaryHelper.augment(summary, stbe.behaviour.freqId);
        }
        augmented.divideAndSendTo(player, pos);
    }
}