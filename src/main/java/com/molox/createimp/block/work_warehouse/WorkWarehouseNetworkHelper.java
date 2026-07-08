package com.molox.createimp.block.work_warehouse;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorkWarehouseNetworkHelper {

    public static int countAvailableWorkWarehouses(UUID freqId) {
        if (freqId == null) {
            return 0;
        }
        int count = 0;
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(freqId, false)) {
            if (link.blockEntity instanceof WorkWarehouseBlockEntity warehouse
                    && !warehouse.getAddress().isEmpty()
                    && !warehouse.isWorking()) {
                count++;
            }
        }
        return count;
    }

    public static boolean hasAvailableWorkWarehouse(UUID freqId) {
        return countAvailableWorkWarehouses(freqId) > 0;
    }

    public static WorkWarehouseBlockEntity findAvailableWorkWarehouse(UUID freqId) {
        if (freqId == null) {
            return null;
        }
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(freqId, false)) {
            if (link.blockEntity instanceof WorkWarehouseBlockEntity warehouse
                    && !warehouse.getAddress().isEmpty()
                    && !warehouse.isWorking()) {
                return warehouse;
            }
        }
        return null;
    }

    /**
     * 按顺序找出最多 {@code count} 个当前空闲、地址已配置的工作仓库，
     * 用于"一个模板对应一个不同的工作仓库"的分配场景。
     * 找不到足够数量时，返回实际能找到的数量（可能少于 count，甚至为空）。
     */
    public static List<WorkWarehouseBlockEntity> findAvailableWorkWarehouses(UUID freqId, int count) {
        List<WorkWarehouseBlockEntity> result = new ArrayList<>();
        if (freqId == null || count <= 0) {
            return result;
        }
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(freqId, false)) {
            if (result.size() >= count) {
                break;
            }
            if (link.blockEntity instanceof WorkWarehouseBlockEntity warehouse
                    && !warehouse.getAddress().isEmpty()
                    && !warehouse.isWorking()) {
                result.add(warehouse);
            }
        }
        return result;
    }
}