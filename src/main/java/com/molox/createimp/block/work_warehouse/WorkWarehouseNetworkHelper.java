package com.molox.createimp.block.work_warehouse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorkWarehouseNetworkHelper {

    public static int countAvailableWorkWarehouses(UUID freqId) {
        if (freqId == null) {
            return 0;
        }
        int count = 0;
        for (WorkWarehouseBlockEntity warehouse : WorkWarehouseBlockEntity.getAllPresent(freqId, false)) {
            if (!warehouse.getAddress().isEmpty() && !warehouse.isWorking()) {
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
        for (WorkWarehouseBlockEntity warehouse : WorkWarehouseBlockEntity.getAllPresent(freqId, false)) {
            if (!warehouse.getAddress().isEmpty() && !warehouse.isWorking()) {
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
        for (WorkWarehouseBlockEntity warehouse : WorkWarehouseBlockEntity.getAllPresent(freqId, false)) {
            if (result.size() >= count) {
                break;
            }
            if (!warehouse.getAddress().isEmpty() && !warehouse.isWorking()) {
                result.add(warehouse);
            }
        }
        return result;
    }

    /**
     * 查询给定物流网络内，是否存在至少一个正处于工作状态（isWorking() 为 true）的工作仓库。
     * 供进程面板（ProcessManagerBlockEntity）周期性检查自身激活状态使用，
     * 与"是否空闲可用"的判定（countAvailableWorkWarehouses 系列）互不影响：
     * 这里不检查地址是否配置，只关心"工作中"这一个状态本身。
     */
    public static boolean hasWorkingWorkWarehouse(UUID freqId) {
        if (freqId == null) {
            return false;
        }
        for (WorkWarehouseBlockEntity warehouse : WorkWarehouseBlockEntity.getAllPresent(freqId, false)) {
            if (warehouse.isWorking()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找出给定物流网络内，所有当前处于工作状态的工作仓库。
     * {@code clientSide} 传 true 时查询客户端自己的注册表（用于进程面板界面
     * 展示当前所有进行中的工作仓库进程），传 false 时查询服务端注册表。
     */
    public static List<WorkWarehouseBlockEntity> findWorkingWorkWarehouses(UUID freqId, boolean clientSide) {
        List<WorkWarehouseBlockEntity> result = new ArrayList<>();
        if (freqId == null) {
            return result;
        }
        for (WorkWarehouseBlockEntity warehouse : WorkWarehouseBlockEntity.getAllPresent(freqId, clientSide)) {
            if (warehouse.isWorking()) {
                result.add(warehouse);
            }
        }
        return result;
    }
}