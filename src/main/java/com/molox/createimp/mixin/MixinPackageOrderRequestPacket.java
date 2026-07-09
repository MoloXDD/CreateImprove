package com.molox.createimp.mixin;

import com.molox.createimp.block.template_panel.TemplateMaterialCalculator;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlock;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderRequestPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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

        List<BigItemStack> filtered = new ArrayList<>();
        List<TemplateMaterialCalculator.RequestEntry> calcEntries = new ArrayList<>();
        List<TemplateMaterialCalculator.OrderedTemplate> orderedTemplates = new ArrayList<>();

        for (BigItemStack entry : stacks) {
            if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(entry.stack);
                if (target != null) {
                    TemplateMaterialCalculator.OrderedTemplate ordered =
                            new TemplateMaterialCalculator.OrderedTemplate(target, entry.count);
                    calcEntries.add(TemplateMaterialCalculator.RequestEntry.ofTemplate(ordered));
                    orderedTemplates.add(ordered);
                }
            } else {
                filtered.add(entry);
                calcEntries.add(TemplateMaterialCalculator.RequestEntry.ofRegular(entry.stack.copy(), entry.count));
            }
        }

        if (orderedTemplates.isEmpty()) {
            return;
        }

        if (be.behaviour != null) {
            List<WorkWarehouseBlockEntity> warehouses = WorkWarehouseNetworkHelper.findAvailableWorkWarehouses(
                    be.behaviour.freqId, orderedTemplates.size());
            if (!warehouses.isEmpty()) {
                Level level = be.getLevel();
                TemplateMaterialCalculator.Result result =
                        TemplateMaterialCalculator.calculate(level, be.behaviour.freqId, calcEntries);

                int assignCount = Math.min(warehouses.size(), orderedTemplates.size());
                for (int i = 0; i < assignCount; i++) {
                    WorkWarehouseBlockEntity warehouse = warehouses.get(i);
                    TemplateMaterialCalculator.OrderedTemplate ordered = orderedTemplates.get(i);
                    TemplateOrderTarget target = ordered.target();
                    warehouse.activate(this.address);
                    warehouse.setTemplateSnapshot(result.snapshotPerTemplate().get(i));
                    warehouse.setDemandList(result.usedFromStockPerTemplate().get(i));
                    warehouse.setRequestedProduct(target.display(), ordered.amount());
                    warehouse.startMaterialRequestStage();
                }
            }
        }

        PackageOrderWithCrafts strippedOrder = new PackageOrderWithCrafts(new PackageOrder(filtered), this.order.orderedCrafts());

        // 目标地址是工作仓库的特殊地址"/back"时，最终产物直接放回连接库存，
        // 不走打包机——这份请求里如果同时混着普通物品，这些普通物品原本
        // 会被编程进红石请求器继续正常发货，但"/back"这个地址对普通物品的
        // 收货逻辑并不适用（它只是工作仓库自己认识的一个特殊地址，不是一个
        // 真实存在的、可以被打包机送达的地址），所以这里直接把普通物品的
        // 请求部分整体取消，不编程进红石请求器。
        String backAddress = com.molox.createimp.CreateImp.getConfig().workWarehouseConfig.backToConnectedInventoryAddress;
        if (!backAddress.isBlank() && backAddress.equals(this.address)) {
            strippedOrder = PackageOrderWithCrafts.empty();
        }

        if (!strippedOrder.isEmpty()) {
            AllSoundEvents.CONFIRM.playOnServer(be.getLevel(), be.getBlockPos());
        }
        player.closeContainer();
        RedstoneRequesterBlock.programRequester(player, be, strippedOrder, this.address);
        ci.cancel();
    }
}