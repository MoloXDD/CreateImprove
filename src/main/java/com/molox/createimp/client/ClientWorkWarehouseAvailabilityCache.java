package com.molox.createimp.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端缓存：记录服务端最近一次回复的"某个物流网络频率下有多少可用工作
 * 仓库"。这份数据只应该通过 {@code WorkWarehouseAvailabilityPacket} 的
 * 服务端权威回应来更新——客户端自己永远不应该在本地重新计算这个数字，
 * 工作仓库完全可能不在客户端当前已加载的区块范围内，本地天生不可能算出
 * 准确结果，尤其是在客户端与服务端分属两个独立进程的真实多人环境下。
 */
public final class ClientWorkWarehouseAvailabilityCache {

    private static final Map<UUID, Integer> COUNTS = new ConcurrentHashMap<>();

    private ClientWorkWarehouseAvailabilityCache() {}

    public static void update(UUID freqId, int count) {
        if (freqId != null) {
            COUNTS.put(freqId, count);
        }
    }

    /**
     * 还没收到过服务端回应时返回 -1（未知）。调用方应当把 -1 视为
     * "暂时不能确定，先按不可用处理"，而不是当成 0——避免把"还没问到"
     * 误判成"确实没有"，两者含义不同。
     */
    public static int get(UUID freqId) {
        if (freqId == null) {
            return -1;
        }
        return COUNTS.getOrDefault(freqId, -1);
    }
}