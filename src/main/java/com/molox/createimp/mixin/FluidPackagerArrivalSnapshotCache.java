package com.molox.createimp.util;

import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.foundation.utility.TickBasedCache;

/**
 * 按物理仓库身份（{@link InventoryIdentifier}）共享流体打包机"上次观察到的
 * 库存快照"，用途与 {@link PackagerArrivalSnapshotCache} 完全一致，专门修复
 * 流体打包机自己一套独立到货通知实现里的同一类重复扣减承诺问题（见
 * {@code MixinFluidPackagerArrivalDedup}）。
 * <p>
 * 单独开一份缓存、不与物品打包机共用同一份，是因为两者存进去的
 * {@link InventorySummary} 内容格式不同（物品打包机存的是真实物品，流体
 * 打包机存的是压缩罐这种"键"再配合各自数量）——万一同一个物理坐标恰好
 * 同时被一台物品打包机和一台流体打包机瞄准、算出了相同的
 * {@link InventoryIdentifier}，共用一份缓存会导致两边互相用对方格式的快照
 * 覆盖，两边的去重都会算错。
 */
public class FluidPackagerArrivalSnapshotCache {

    private static final TickBasedCache<InventoryIdentifier, InventorySummary> CACHE =
            new TickBasedCache<>(100, true);

    private FluidPackagerArrivalSnapshotCache() {
    }

    public static InventorySummary get(InventoryIdentifier id) {
        return CACHE.getIfPresent(id);
    }

    public static void put(InventoryIdentifier id, InventorySummary summary) {
        CACHE.put(id, summary);
    }
}