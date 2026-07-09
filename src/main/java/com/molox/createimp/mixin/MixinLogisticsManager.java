package com.molox.createimp.mixin;

import com.molox.createimp.block.template_panel.TemplateMaterialCalculator;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.world.level.Level;
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
            return order;
        }

        List<WorkWarehouseBlockEntity> warehouses =
                WorkWarehouseNetworkHelper.findAvailableWorkWarehouses(freqId, orderedTemplates.size());
        if (!warehouses.isEmpty()) {
            Level level = warehouses.get(0).getLevel();
            TemplateMaterialCalculator.Result result =
                    TemplateMaterialCalculator.calculate(level, freqId, calcEntries);

            int assignCount = Math.min(warehouses.size(), orderedTemplates.size());
            for (int i = 0; i < assignCount; i++) {
                WorkWarehouseBlockEntity warehouse = warehouses.get(i);
                TemplateMaterialCalculator.OrderedTemplate ordered = orderedTemplates.get(i);
                TemplateOrderTarget target = ordered.target();
                warehouse.activate(address);
                warehouse.setTemplateSnapshot(result.snapshotPerTemplate().get(i));
                warehouse.setDemandList(result.usedFromStockPerTemplate().get(i));
                warehouse.setRequestedProduct(target.display(), ordered.amount());
                warehouse.startMaterialRequestStage();
            }
        }

        // 目标地址是工作仓库的特殊地址"/back"时，最终产物直接放回连接库存，
        // 不走打包机——这份请求里如果同时混着普通物品，这些普通物品原本
        // 会被当作正常请求继续走打包机发货流程，但"/back"这个地址对普通
        // 物品的收货逻辑并不适用（它只是工作仓库自己认识的一个特殊地址，
        // 不是一个真实存在的、可以被打包机送达的地址），所以这里直接把
        // 普通物品的请求部分整体取消，不让它们继续往下走。
        String backAddress = com.molox.createimp.CreateImp.getConfig().workWarehouseConfig.backToConnectedInventoryAddress;
        if (!backAddress.isBlank() && backAddress.equals(address)) {
            filtered = new ArrayList<>();
        }

        return new PackageOrderWithCrafts(new PackageOrder(filtered), order.orderedCrafts());
    }
}