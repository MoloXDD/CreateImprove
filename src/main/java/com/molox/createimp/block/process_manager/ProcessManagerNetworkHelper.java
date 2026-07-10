package com.molox.createimp.block.process_manager;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProcessManagerNetworkHelper {

    /**
     * 找出给定物流网络内，当前所有现存的进程面板。{@code clientSide} 传
     * true 时查询客户端自己的缓存，传 false 时查询服务端缓存——工作仓库
     * 归档历史日志时用 false（服务端权威数据）。
     */
    public static List<ProcessManagerBlockEntity> findAll(UUID freqId, boolean clientSide) {
        List<ProcessManagerBlockEntity> result = new ArrayList<>();
        if (freqId == null) {
            return result;
        }
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(freqId, false, clientSide)) {
            if (link.blockEntity instanceof ProcessManagerBlockEntity pmbe) {
                result.add(pmbe);
            }
        }
        return result;
    }
}