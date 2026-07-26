package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.template_panel.TemplateMaterialCalculator;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterEffectPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 红石请求器接收脉冲、触发 {@code triggerRequest} 时，若配置里含有模板，
 * 接管为本模组自己的一套"普通物品 + 模板共用同一份库存、按格子分配工作
 * 仓库"的判定与发货逻辑，取代原版单纯逐项对比库存的判定；不含模板时完全
 * 不介入，原版行为（含"允许部分请求"开关的原有语义）不受任何影响。
 * <p>
 * 只在服务端有实际意义——{@code triggerRequest} 只会被服务端调用到（见
 * {@code RedstoneRequesterBlock.neighborChanged} 里的 isClientSide 提前
 * 返回），本类不引用任何客户端专属类型，双端都能安全加载。
 */
@Mixin(value = RedstoneRequesterBlockEntity.class, remap = false)
public abstract class MixinRedstoneRequesterBlockEntity {

    @Inject(method = "triggerRequest", at = @At("HEAD"), cancellable = true)
    private void createimp$handleTemplateRequest(CallbackInfo ci) {
        RedstoneRequesterBlockEntity self = (RedstoneRequesterBlockEntity) (Object) this;
        List<BigItemStack> stacks = self.encodedRequest.stacks();

        boolean hasTemplate = false;
        for (BigItemStack entry : stacks) {
            if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                hasTemplate = true;
                break;
            }
        }
        if (!hasTemplate) {
            // 不含模板，完全不介入，原版 triggerRequest 照常执行。
            return;
        }
        ci.cancel();

        Level level = self.getLevel();
        UUID freqId = self.behaviour != null ? self.behaviour.freqId : null;
        if (level == null || level.isClientSide() || freqId == null) {
            createimp$fail(self);
            return;
        }

        String address = self.encodedTargetAdress;
        String backAddress = CreateImp.getConfig().templateFunctionConfig.backToConnectedInventoryAddress;
        boolean routedToWarehouse = !backAddress.isBlank() && backAddress.equals(address);

        List<BigItemStack> regular = new ArrayList<>();
        List<TemplateMaterialCalculator.OrderedTemplate> orderedTemplates = new ArrayList<>();

        for (BigItemStack entry : stacks) {
            if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(entry.stack);
                if (target != null) {
                    orderedTemplates.add(new TemplateMaterialCalculator.OrderedTemplate(target, entry.count));
                }
            } else if (!routedToWarehouse) {
                // 目标地址是工作仓库的连接库存路由地址时，直接无视普通物品：
                // 既不参与材料计算（不占共享库存），也不会被发出去。
                regular.add(entry);
            }
        }

        List<WorkWarehouseBlockEntity> warehouses =
                WorkWarehouseNetworkHelper.findAvailableWorkWarehouses(freqId, orderedTemplates.size());

        if (!self.allowPartialRequests && warehouses.size() < orderedTemplates.size()) {
            // 不允许部分请求时，工作仓库数量不够可以在做材料计算之前就直接
            // 判定失败，省去一次没有意义的库存查询。
            createimp$fail(self);
            return;
        }

        TemplateMaterialCalculator.PartialResult result = TemplateMaterialCalculator.calculatePartial(
                level, freqId, regular, orderedTemplates, warehouses.size(), true);

        if (result.isEmpty()) {
            createimp$fail(self);
            return;
        }
        if (!self.allowPartialRequests && !result.isFullMatch(orderedTemplates.size())) {
            createimp$fail(self);
            return;
        }

        int warehouseIndex = 0;
        for (TemplateMaterialCalculator.TemplateDispatch dispatch : result.templatesToActivate()) {
            WorkWarehouseBlockEntity warehouse = warehouses.get(warehouseIndex++);
            TemplateOrderTarget target = dispatch.template().target();
            warehouse.activate(address);
            warehouse.setTemplateSnapshot(dispatch.snapshot());
            warehouse.setDemandList(dispatch.demand());
            warehouse.setRequestedProduct(target.display(), dispatch.amount());
            warehouse.startMaterialRequestStage();
        }

        List<BigItemStack> toSend = new ArrayList<>();
        for (TemplateMaterialCalculator.RegularFulfillment fulfillment : result.regularFulfillments()) {
            if (fulfillment.sent() > 0) {
                toSend.add(new BigItemStack(fulfillment.requested().stack.copy(), fulfillment.sent()));
            }
        }

        boolean anySucceeded = !result.templatesToActivate().isEmpty();
        if (!toSend.isEmpty()) {
            PackageOrderWithCrafts filteredOrder =
                    new PackageOrderWithCrafts(new PackageOrder(toSend), self.encodedRequest.orderedCrafts());
            boolean sent = self.broadcastPackageRequest(LogisticallyLinkedBehaviour.RequestType.REDSTONE,
                    filteredOrder, null, address);
            anySucceeded = anySucceeded || sent;
        }

        createimp$succeed(self, anySucceeded);
    }

    private static void createimp$fail(RedstoneRequesterBlockEntity self) {
        self.lastRequestSucceeded = false;
        createimp$sendEffect(self, false);
    }

    private static void createimp$succeed(RedstoneRequesterBlockEntity self, boolean cosmeticSuccess) {
        self.lastRequestSucceeded = true;
        createimp$sendEffect(self, cosmeticSuccess);
    }

    private static void createimp$sendEffect(RedstoneRequesterBlockEntity self, boolean success) {
        Level level = self.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            CatnipServices.NETWORK.sendToClientsAround(serverLevel, (Vec3i) self.getBlockPos(), 32.0,
                    new RedstoneRequesterEffectPacket(self.getBlockPos(), success));
        }
    }
}