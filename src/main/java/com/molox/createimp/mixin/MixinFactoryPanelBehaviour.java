package com.molox.createimp.mixin;

import com.molox.createimp.util.IFactoryPanelBehaviourDemandMode;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class MixinFactoryPanelBehaviour implements IFactoryPanelBehaviourDemandMode {

    @Unique
    private boolean createimp$demandMode = false;

    // 仅 Shadow FactoryPanelBehaviour 自身声明的字段
    @Shadow public Map<FactoryPanelPosition, FactoryPanelConnection> targetedBy;
    @Shadow public boolean satisfied;
    @Shadow public boolean promisedSatisfied;
    @Shadow public boolean waitingForNetwork;
    @Shadow public boolean redstonePowered;
    @Shadow public int recipeOutput;
    @Shadow public String recipeAddress;
    @Shadow public List<ItemStack> activeCraftingArrangement;
    @Shadow public boolean active;
    @Shadow public FactoryPanelBlock.PanelSlot slot;
    @Shadow public UUID network;
    @Shadow private int timer;

    // 仅 Shadow FactoryPanelBehaviour 自身声明的方法
    @Shadow public abstract boolean isActive();
    @Shadow public abstract int getLevelInStorage();
    @Shadow public abstract int getPromised();
    @Shadow public abstract com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity panelBE();
    @Shadow private native void resetTimer();
    @Shadow private native void sendEffect(FactoryPanelPosition from, boolean success);
    @Shadow private native int getConfigRequestIntervalInTicks();

    // ---- 继承自 FilteringBehaviour 的成员，全部通过强转访问 ----
    @Unique
    private FilteringBehaviour createimp$asFiltering() {
        return (FilteringBehaviour) (Object) this;
    }

    @Unique
    private ItemStack createimp$getFilter() {
        return createimp$asFiltering().getFilter();
    }

    @Unique
    private int createimp$getAmount() {
        return createimp$asFiltering().getAmount();
    }

    @Unique
    private boolean createimp$isUpTo() {
        return createimp$asFiltering().upTo;
    }

    // ---- 继承自 BlockEntityBehaviour 的 blockEntity 字段 ----
    @Unique
    private net.minecraft.world.level.Level createimp$getLevel() {
        return ((com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour) (Object) this)
                .blockEntity.getLevel();
    }

    @Override
    public boolean createimp$isDemandMode() {
        return createimp$demandMode;
    }

    @Override
    public void createimp$setDemandMode(boolean value) {
        createimp$demandMode = value;
    }

    @Inject(method = "tickRequests", at = @At("HEAD"), cancellable = true)
    private void createimp$tickRequests(CallbackInfo ci) {
        if (!createimp$demandMode) return;

        var panelBE = panelBE();
        if (targetedBy.isEmpty() && !panelBE.restocker) {
            if (createimp$demandMode) {
                createimp$demandMode = false;
                panelBE.setChanged();
            }
            return;
        }
        if (panelBE.restocker) return;

        if (satisfied || promisedSatisfied || waitingForNetwork || redstonePowered) {
            ci.cancel();
            return;
        }

        if (timer > 0) {
            timer = Math.min(timer, getConfigRequestIntervalInTicks());
            --timer;
            ci.cancel();
            return;
        }
        resetTimer();

        if (recipeAddress.isBlank()) {
            ci.cancel();
            return;
        }

        ItemStack filterItem = createimp$getFilter();
        if (filterItem.isEmpty() || recipeOutput <= 0) {
            ci.cancel();
            return;
        }

        int demand = createimp$getAmount() * (createimp$isUpTo() ? 1 : filterItem.getMaxStackSize());
        int inStorage = getLevelInStorage();
        int promised = getPromised();
        int gap = demand - inStorage - promised;

        if (gap <= 0) {
            ci.cancel();
            return;
        }

        int batchesNeeded = (gap + recipeOutput - 1) / recipeOutput;

        // 第一轮遍历：先收集每种原料的"单批总需求"(不乘batchesNeeded)，
        // 即同一物品来自多个connection时，各connection.amount的总和。
        HashMap<UUID, Map<ItemStack, FactoryPanelBehaviour.ItemStackConnections>> consolidated = new HashMap<>();

        for (FactoryPanelConnection connection : targetedBy.values()) {
            FactoryPanelBehaviour source = FactoryPanelBehaviour.at(
                    (BlockAndTintGetter) createimp$getLevel(), connection);
            if (source == null) {
                ci.cancel();
                return;
            }
            ItemStack item = source.getFilter();
            if (item.isEmpty()) {
                ci.cancel();
                return;
            }

            Map<ItemStack, FactoryPanelBehaviour.ItemStackConnections> networkMap =
                    consolidated.computeIfAbsent(source.network,
                            $ -> new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG));
            networkMap.computeIfAbsent(item, $ -> new FactoryPanelBehaviour.ItemStackConnections(item));
            FactoryPanelBehaviour.ItemStackConnections isc = networkMap.get(item);
            isc.add(connection);
            // 这里先只累加"单批所需总量"，不乘batchesNeeded，
            // 留到下面按实际库存反推可执行批次时再统一相乘。
            isc.totalAmount += connection.amount;
        }

        // 第二轮：按各物品的实际网络库存，反推"这种物品最多能撑几个完整批次"，
        // 取所有物品里的最小值，并与原计划batchesNeeded取较小者，
        // 得到这次实际要执行的批次数。这样无论哪种原料库存不足，
        // 发出的请求始终是"单批所需总量"的整数倍，不会出现半批材料。
        int actualBatches = batchesNeeded;
        for (Map.Entry<UUID, Map<ItemStack, FactoryPanelBehaviour.ItemStackConnections>> entry
                : consolidated.entrySet()) {
            UUID net = entry.getKey();
            InventorySummary summary = LogisticsManager.getSummaryOfNetwork(net, true);
            for (FactoryPanelBehaviour.ItemStackConnections isc : entry.getValue().values()) {
                int perBatchNeed = isc.totalAmount;
                if (perBatchNeed <= 0 || isc.item.isEmpty()) {
                    actualBatches = 0;
                    continue;
                }
                int available = summary.getCountOf(isc.item);
                int batchesThisItemCanSupport = available / perBatchNeed;
                actualBatches = Math.min(actualBatches, batchesThisItemCanSupport);
            }
        }

        if (actualBatches <= 0) {
            // 一个完整批次都凑不出，本次彻底放弃，不发出任何请求，
            // 等待下次tick库存补充后重试。
            for (Map<ItemStack, FactoryPanelBehaviour.ItemStackConnections> networkMap : consolidated.values()) {
                for (FactoryPanelBehaviour.ItemStackConnections isc : networkMap.values()) {
                    for (FactoryPanelConnection conn : isc) {
                        sendEffect(conn.from, false);
                    }
                }
            }
            ci.cancel();
            return;
        }

        // 第三轮：用actualBatches重新计算每种物品的实际请求总量并发出。
        HashMultimap<UUID, BigItemStack> toRequest = HashMultimap.create();
        for (Map.Entry<UUID, Map<ItemStack, FactoryPanelBehaviour.ItemStackConnections>> entry
                : consolidated.entrySet()) {
            UUID net = entry.getKey();
            for (FactoryPanelBehaviour.ItemStackConnections isc : entry.getValue().values()) {
                long actualTotal = (long) isc.totalAmount * actualBatches;
                int clamped = (int) Math.min(actualTotal, Integer.MAX_VALUE);
                toRequest.put(net, new BigItemStack(isc.item, clamped));
                for (FactoryPanelConnection conn : isc) {
                    sendEffect(conn.from, true);
                }
            }
        }

        PackageOrderWithCrafts craftContext = PackageOrderWithCrafts.empty();
        if (!activeCraftingArrangement.isEmpty()) {
            // 不能用PackageOrderWithCrafts.singleRecipe()——它构造出的CraftingEntry.count()
            // 永远硬编码为1，会丢失真实批次数。这里用actualBatches（按库存反推后
            // 实际能执行的批次数），而不是原计划的batchesNeeded，
            // 确保理包机收到的配方声明量与实际送出的材料量完全一致。
            craftContext = new PackageOrderWithCrafts(
                    PackageOrder.empty(),
                    List.of(new PackageOrderWithCrafts.CraftingEntry(
                            new PackageOrder(activeCraftingArrangement.stream()
                                    .map(s -> new BigItemStack(s.copyWithCount(1)))
                                    .toList()),
                            actualBatches)));
        }

        ArrayList<Multimap<PackagerBlockEntity, PackagingRequest>> requests = new ArrayList<>();
        for (Map.Entry<UUID, Collection<BigItemStack>> entry : toRequest.asMap().entrySet()) {
            PackageOrderWithCrafts order = new PackageOrderWithCrafts(
                    new PackageOrder(new ArrayList<>(entry.getValue())),
                    craftContext.orderedCrafts());
            requests.add(LogisticsManager.findPackagersForRequest(
                    entry.getKey(), order, null, recipeAddress));
        }

        for (var multimap : requests) {
            for (PackagerBlockEntity packager : multimap.keySet()) {
                if (packager.isTooBusyFor(LogisticallyLinkedBehaviour.RequestType.RESTOCK)) {
                    ci.cancel();
                    return;
                }
            }
        }

        for (var multimap : requests) {
            LogisticsManager.performPackageRequests(multimap);
        }

        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(network);
        if (promises != null) {
            // 承诺量必须用actualBatches而不是原计划的batchesNeeded，
            // 否则会向库存系统过度声明"即将收到的产物量"，
            // 但实际合成出来的产物只有actualBatches批，造成promisedSatisfied误判。
            promises.add(new RequestPromise(
                    new BigItemStack(filterItem, actualBatches * recipeOutput)));
        }

        panelBE.advancements.awardPlayer(AllAdvancements.FACTORY_GAUGE);
        ci.cancel();
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void createimp$write(CompoundTag nbt, HolderLookup.Provider registries,
                                 boolean clientPacket, CallbackInfo ci) {
        if (!isActive()) return;
        if (targetedBy.isEmpty()) {
            createimp$demandMode = false;
        }
        CompoundTag panelTag = nbt.getCompound(CreateLang.asId(slot.name()));
        panelTag.putBoolean("CreateImpDemandMode", createimp$demandMode);
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createimp$read(CompoundTag nbt, HolderLookup.Provider registries,
                                boolean clientPacket, CallbackInfo ci) {
        if (!isActive()) return;
        CompoundTag panelTag = nbt.getCompound(CreateLang.asId(slot.name()));
        createimp$demandMode = panelTag.getBoolean("CreateImpDemandMode");
    }
}