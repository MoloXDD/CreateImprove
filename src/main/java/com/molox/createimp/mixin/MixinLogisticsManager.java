package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.template_panel.TemplateMaterialCalculator;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.molox.createimp.util.PackagerSignAddressHelper;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Mixin(value = LogisticsManager.class, remap = false)
public abstract class MixinLogisticsManager {

    @Shadow
    private static Random r;

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
        String backAddress = com.molox.createimp.CreateImp.getConfig().templateFunctionConfig.backToConnectedInventoryAddress;
        if (!backAddress.isBlank() && backAddress.equals(address)) {
            filtered = new ArrayList<>();
        }

        return new PackageOrderWithCrafts(new PackageOrder(filtered), order.orderedCrafts());
    }

    // 拦截 findPackagersForRequest 内部"同一目标库存被多个打包机瞄准时，
    // 从候选链表里随机选一个"的那次 List.get(int) 调用（ordinal=0，经字节码核实，
    // 是该方法体内第一次出现的 List.get(int)，第二次出现在后面 stacks.get(i) 处，
    // 与此处无关）。若 Create 未来版本调整了这段代码的书写顺序，这个 ordinal
    // 需要重新核实。
    @Redirect(method = "findPackagersForRequest",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0))
    private static Object createimp$selectPackagerLinkByAddress(
            List<LogisticallyLinkedBehaviour> linkGroup, int index,
            UUID freqId, PackageOrderWithCrafts order, IdentifiedInventory ignoredHandler, String address) {
        if (linkGroup.size() <= 1) {
            return linkGroup.get(index);
        }
        if (!CreateImp.getConfig().functionConfig.featureToggles.packagerAddressFilterEnabled) {
            return linkGroup.get(index);
        }

        List<LogisticallyLinkedBehaviour> matched = new ArrayList<>();
        List<LogisticallyLinkedBehaviour> noSign = new ArrayList<>();

        for (LogisticallyLinkedBehaviour link : linkGroup) {
            if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe)) {
                continue;
            }
            PackagerBlockEntity packager = plbe.getPackager();
            if (packager == null) {
                continue;
            }
            String signAddress = PackagerSignAddressHelper.resolveSignAddress(packager);
            if (signAddress == null) {
                noSign.add(link);
            } else if (PackageItem.matchAddress(signAddress, address)) {
                matched.add(link);
            }
        }

        if (!matched.isEmpty()) {
            return matched.get(r.nextInt(matched.size()));
        }
        if (!noSign.isEmpty()) {
            return noSign.get(r.nextInt(noSign.size()));
        }
        return linkGroup.get(index);
    }
}