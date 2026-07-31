package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.util.FluidPackagerArrivalSnapshotCache;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 流体打包机（流体包裹自己的 {@code FluidPackagerBlockEntity}）同样
 * 存在"多台打包机贴着同一个物理仓库时，各自独立观察到同一次入库，导致同一批
 * 到货被重复扣减多次承诺"的问题——原理与 {@link MixinPackagerArrivalDedup}
 * 修复的 Create 原版问题完全一致，但流体打包机到货通知走的是流体包裹自己
 * 独立实现的一套（{@code ResourcePackagerEngine} 内部按打包机实例私有维护
 * 快照，再交给 {@code ResourcePackagerPromiseHelper.notifyNewArrivals}
 * 扣减承诺），Create 原版那个 Mixin 管不到，需要单独修一份。
 * <p>
 * 【版本兼容注意】流体包裹 1.2.5 里，扫描库存并调用 {@code notifyNewArrivals}
 * 这段逻辑直接写在 {@code ResourcePackagerEngine.getAvailableResources}
 * 方法体内；1.2.6 把这段逻辑整体挪进了新增的私有方法
 * {@code refreshAvailableResources(ResourcePackager, RuntimeState)}，
 * {@code getAvailableResources} 现在只是调用它并多包一层当前 tick 的缓存
 * 命中检查。{@code notifyNewArrivals} 本身的签名和内部实现两个版本完全一致，
 * 变的只是它被从哪个方法里调用——所以下面 {@code @Redirect} 的 {@code method}
 * 目标是 {@code refreshAvailableResources}，不是 {@code getAvailableResources}。
 * 之后流体包裹再更新，如果这个 Mixin 又报"Scanned 0 target(s)"，先反编译确认
 * 这次调用点又挪到哪个方法里了，再对应改这里的 {@code method}，不要凭猜测改。
 * <p>
 * 【本类为什么直接重新实现一遍到货通知逻辑，而不是像
 * {@code MixinPackagerArrivalDedup} 那样单纯替换参数后调用原方法】
 * 反编译确认 {@code ResourcePackagerPromiseHelper} 这个类本身是包级私有
 * （非 public），本模组所在包无法在源码里直接引用它、也就没法像物品那边
 * 一样借助 {@code @Shadow} 拿到原方法调用权限。这里改为把这次调用整个
 * 替换掉，按反编译确认的原始逻辑（扫描六个方向上的工厂仪表补货器/标码
 * 无线红石信号终端频道，找到对应的库存承诺队列，把"新增了多少"通知给
 * 它们）用公开的 Create API 重新实现一遍，只是把"之前是多少"换成按物理
 * 仓库身份共享的快照。
 * <p>
 * 【为什么 Mixin 目标用字符串而不是 .class】流体包裹是可选依赖，
 * {@code ResourcePackagerEngine} 只有装了流体包裹才存在——用 {@code
 * targets} 字符串而不是 {@code value = X.class} 是为了避免 Mixin 处理本类
 * 注解时就去解析这个类型；由于本类唯一的 Mixin 目标就是这个流体包裹专属
 * 类，只有流体包裹已加载、这个类被真正加载到时，本类合并进去的代码才会
 * 有机会执行，因此方法体内直接引用的 {@code FluidPackagerBlockEntity} 等
 * 类型（经由 {@link TemplateFluidDisplayHelper} 间接引用）不会在未安装
 * 流体包裹时被提前触发加载。
 */
@Mixin(targets = "com.yision.fluidlogistics.content.logistics.packageResource.ResourcePackagerEngine", remap = false)
public abstract class MixinFluidPackagerArrivalDedup {

    @Redirect(method = "refreshAvailableResources", at = @At(value = "INVOKE",
            target = "Lcom/yision/fluidlogistics/content/logistics/packageResource/ResourcePackagerPromiseHelper;"
                    + "notifyNewArrivals(Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;"
                    + "Lcom/simibubi/create/content/logistics/packager/InventorySummary;"
                    + "Lcom/simibubi/create/content/logistics/packager/InventorySummary;)V"))
    private static void createimp$notifyNewArrivalsDeduped(PackagerBlockEntity owner,
                                                           InventorySummary before, InventorySummary after) {
        InventorySummary effectiveBefore = before;
        if (FluidLogisticsCompat.isLoaded()
                && CreateImp.getConfig().modCompatConfig.fluidLogisticsCompat.fixFluidPackagerDuplicatePromiseConsumption) {
            InventoryIdentifier id = TemplateFluidDisplayHelper.identifyFluidPackagerTarget(owner);
            if (id != null) {
                InventorySummary shared = FluidPackagerArrivalSnapshotCache.get(id);
                effectiveBefore = shared != null ? shared : before;
                // after会在这次调用之后继续被ResourcePackagerEngine自己的
                // 私有快照记下，这里存入共享缓存的是它的独立副本，避免与
                // 引擎自身持有的实例产生别名污染。
                FluidPackagerArrivalSnapshotCache.put(id, after.copy());
            }
        }
        createimp$notifyNewArrivals(owner, effectiveBefore, after);
    }

    /**
     * 按反编译确认的 {@code ResourcePackagerPromiseHelper.notifyNewArrivals}
     * 原始逻辑重新实现：扫描打包机六个方向，找处于补货模式且激活中的工厂
     * 仪表面板、以及挂载了待处理承诺的标码无线红石信号终端频道，把这次
     * "新增了多少"通知给对应的库存承诺队列。
     */
    private static void createimp$notifyNewArrivals(PackagerBlockEntity packager,
                                                    InventorySummary before, InventorySummary after) {
        if (before == null || after.isEmpty()) {
            return;
        }
        Level level = packager.getLevel();
        if (level == null) {
            return;
        }
        Set<RequestPromiseQueue> promiseQueues = new HashSet<>();
        BlockPos packagerPos = packager.getBlockPos();
        for (Direction direction : Iterate.directions) {
            BlockPos adjacentPos = packagerPos.relative(direction);
            if (!level.isLoaded(adjacentPos)) {
                continue;
            }
            BlockState adjacentState = level.getBlockState(adjacentPos);
            if (AllBlocks.FACTORY_GAUGE.has(adjacentState)
                    && FactoryPanelBlock.connectedDirection(adjacentState) == direction
                    && level.getBlockEntity(adjacentPos) instanceof FactoryPanelBlockEntity panel) {
                if (panel.restocker) {
                    for (FactoryPanelBehaviour behaviour : panel.panels.values()) {
                        if (behaviour.isActive()) {
                            promiseQueues.add(behaviour.restockerPromises);
                        }
                    }
                }
            }
            if (AllBlocks.STOCK_LINK.has(adjacentState)
                    && PackagerLinkBlock.getConnectedDirection(adjacentState) == direction
                    && level.getBlockEntity(adjacentPos) instanceof PackagerLinkBlockEntity link) {
                UUID frequencyId = link.behaviour.freqId;
                if (Create.LOGISTICS.hasQueuedPromises(frequencyId)) {
                    promiseQueues.add(Create.LOGISTICS.getQueuedPromises(frequencyId));
                }
            }
        }
        if (promiseQueues.isEmpty()) {
            return;
        }
        for (BigItemStack entry : after.getStacks()) {
            int increase = entry.count - before.getCountOf(entry.stack);
            if (increase <= 0) {
                continue;
            }
            for (RequestPromiseQueue queue : promiseQueues) {
                queue.itemEnteredSystem(entry.stack, increase);
            }
        }
    }
}