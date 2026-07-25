package com.molox.createimp.util;

import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.foundation.utility.TickBasedCache;

/**
 * 按物理仓库身份（{@link InventoryIdentifier}）共享"上次观察到的库存快照"。
 * <p>
 * Create原版每个打包机都各自维护一份私有的库存快照，用来算出"这次新增了多少"再去
 * 扣减库存承诺队列；当多个打包机贴着同一个物理仓库时，各自独立观察到同一次入库，
 * 导致同一批到货被重复扣减多次承诺。这里把该快照改为按仓库身份共享一份，不管有
 * 多少打包机对接同一个仓库、谁先谁后触发检查，同一次入库只会被计入一次。
 * <p>
 * 复用Create自身的{@link TickBasedCache}（与{@code LogisticsManager}统计网络库存
 * 时用的是同一个工具类），条目一段时间不被访问后自动过期清理，不需要额外维护
 * 生命周期，仓库被拆除或不再被任何打包机对接时也不会造成内存堆积。
 */
public class PackagerArrivalSnapshotCache {

    private static final TickBasedCache<InventoryIdentifier, InventorySummary> CACHE =
            new TickBasedCache<>(100, true);

    private PackagerArrivalSnapshotCache() {
    }

    public static InventorySummary get(InventoryIdentifier id) {
        return CACHE.getIfPresent(id);
    }

    public static void put(InventoryIdentifier id, InventorySummary summary) {
        CACHE.put(id, summary);
    }
}