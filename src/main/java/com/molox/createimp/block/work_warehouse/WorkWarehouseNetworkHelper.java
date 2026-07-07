package com.molox.createimp.block.work_warehouse;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;

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
}