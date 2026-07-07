package com.molox.createimp.mixin;

import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlock;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderRequestPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = PackageOrderRequestPacket.class, remap = false)
public abstract class MixinPackageOrderRequestPacket {

    @Shadow
    private PackageOrderWithCrafts order;

    @Shadow
    private String address;

    @Shadow
    private boolean encodeRequester;

    @Inject(method = "applySettings", at = @At("HEAD"), cancellable = true)
    private void createimp$handleTemplateOrderForRequester(ServerPlayer player, StockTickerBlockEntity be, CallbackInfo ci) {
        if (!this.encodeRequester) {
            return;
        }

        List<BigItemStack> stacks = this.order.orderedStacks().stacks();
        boolean hasToken = false;
        for (BigItemStack entry : stacks) {
            if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                hasToken = true;
                break;
            }
        }
        if (!hasToken) {
            return;
        }

        if (be.behaviour != null) {
            WorkWarehouseBlockEntity warehouse = WorkWarehouseNetworkHelper.findAvailableWorkWarehouse(be.behaviour.freqId);
            if (warehouse != null) {
                warehouse.activate(this.address);
            }
        }

        List<BigItemStack> filtered = new ArrayList<>();
        for (BigItemStack entry : stacks) {
            if (!TemplateOrderTokenHelper.isToken(entry.stack)) {
                filtered.add(entry);
            }
        }
        PackageOrderWithCrafts strippedOrder = new PackageOrderWithCrafts(new PackageOrder(filtered), this.order.orderedCrafts());

        if (!strippedOrder.isEmpty()) {
            AllSoundEvents.CONFIRM.playOnServer(be.getLevel(), be.getBlockPos());
        }
        player.closeContainer();
        RedstoneRequesterBlock.programRequester(player, be, strippedOrder, this.address);
        ci.cancel();
    }
}