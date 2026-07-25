package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.util.PackagerArrivalSnapshotCache;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Create原版打包机检测"库存新增了多少"以扣减库存承诺队列时，依据的是每个打包机
 * 各自私有的库存快照（{@code PackagerBlockEntity.availableItems}）；当多个打包机
 * 贴着同一个物理仓库时，各自都会独立观察到同一次入库，导致同一批到货被重复扣减
 * 多次承诺，使按量请求、补货等依赖承诺判断的功能误判缺口、反复超发请求。
 * <p>
 * 这里只替换"上次观察到的快照"这一个参照值：不再使用打包机自己的私有快照，
 * 改为按{@link InventoryIdentifier}（Create自身已有的物理仓库身份识别机制，
 * {@code LogisticsManager}统计网络库存、分配发货请求时用的是同一套）共享一份
 * 快照。Create原有的承诺扣减、上游连接扫描等逻辑完全不动，只是喂给它更准确的
 * "之前是多少"，因此对Create原版全部打包机发货行为（不论普通请求、补货模式，
 * 还是本模组新增的动力合成/按量请求模式）都会一并生效。
 */
@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class MixinPackagerArrivalDedup {

    @Shadow
    private native void submitNewArrivals(InventorySummary before, InventorySummary after);

    @Redirect(method = "getAvailableItems", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;submitNewArrivals(Lcom/simibubi/create/content/logistics/packager/InventorySummary;Lcom/simibubi/create/content/logistics/packager/InventorySummary;)V"))
    private void createimp$submitNewArrivalsDeduped(PackagerBlockEntity instance,
                                                    InventorySummary before, InventorySummary after) {
        if (!CreateImp.getConfig().fixConfig.fixDuplicatePackagerPromiseConsumption) {
            this.submitNewArrivals(before, after);
            return;
        }

        IdentifiedInventory identified = instance.targetInventory.getIdentifiedInventory();
        InventoryIdentifier id = identified == null ? null : identified.identifier();
        if (id == null) {
            // 无法识别为具备稳定身份的物理仓库（例如目标本身不支持该识别机制），
            // 回退到Create原版行为，不做任何改动。
            this.submitNewArrivals(before, after);
            return;
        }

        InventorySummary sharedBefore = PackagerArrivalSnapshotCache.get(id);
        this.submitNewArrivals(sharedBefore != null ? sharedBefore : before, after);
        // after会在submitNewArrivals之后继续被赋值给打包机自己的availableItems字段，
        // 这里存入缓存的是它的独立副本，避免与打包机自身持有的实例产生别名污染。
        PackagerArrivalSnapshotCache.put(id, after.copy());
    }
}