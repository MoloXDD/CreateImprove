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

        HashMap<UUID, Map<ItemStack, FactoryPanelBehaviour.ItemStackConnections>> consolidated = new HashMap<>();
        boolean failed = false;

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

            long totalNeeded = (long) connection.amount * batchesNeeded;
            int clamped = (int) Math.min(totalNeeded, Integer.MAX_VALUE);

            Map<ItemStack, FactoryPanelBehaviour.ItemStackConnections> networkMap =
                    consolidated.computeIfAbsent(source.network,
                            $ -> new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG));
            networkMap.computeIfAbsent(item, $ -> new FactoryPanelBehaviour.ItemStackConnections(item));
            FactoryPanelBehaviour.ItemStackConnections isc = networkMap.get(item);
            isc.add(connection);
            isc.totalAmount += clamped;
        }

        HashMultimap<UUID, BigItemStack> toRequest = HashMultimap.create();
        for (Map.Entry<UUID, Map<ItemStack, FactoryPanelBehaviour.ItemStackConnections>> entry
                : consolidated.entrySet()) {
            UUID net = entry.getKey();
            InventorySummary summary = LogisticsManager.getSummaryOfNetwork(net, true);
            for (FactoryPanelBehaviour.ItemStackConnections isc : entry.getValue().values()) {
                if (isc.totalAmount == 0 || isc.item.isEmpty()
                        || summary.getCountOf(isc.item) < isc.totalAmount) {
                    for (FactoryPanelConnection conn : isc) {
                        sendEffect(conn.from, false);
                    }
                    failed = true;
                    continue;
                }
                toRequest.put(net, new BigItemStack(isc.item, isc.totalAmount));
                for (FactoryPanelConnection conn : isc) {
                    sendEffect(conn.from, true);
                }
            }
        }

        if (failed) {
            ci.cancel();
            return;
        }

        PackageOrderWithCrafts craftContext = PackageOrderWithCrafts.empty();
        if (!activeCraftingArrangement.isEmpty()) {
            craftContext = PackageOrderWithCrafts.singleRecipe(
                    activeCraftingArrangement.stream()
                            .map(s -> new BigItemStack(s.copyWithCount(1)))
                            .toList());
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
            promises.add(new RequestPromise(
                    new BigItemStack(filterItem, batchesNeeded * recipeOutput)));
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