package com.molox.createimp.client;

import com.molox.createimp.util.StockKeeperRequestScreenInvoker;
import net.minecraft.client.Minecraft;

/**
 * 客户端每 tick 调用一次：如果当前打开的界面是仓储管理员请求界面
 * （通过 {@link StockKeeperRequestScreenInvoker} 判断，避免这个类直接
 * 依赖 mixin 包下的具体类型），交给它自己内部判断要不要向服务端发起一次
 * 工作仓库可用数量的查询。没有打开这个界面、或者请求栏里没有模板时，
 * 实际什么都不会发生。
 */
public final class WorkWarehouseAvailabilityPollHandler {

    private WorkWarehouseAvailabilityPollHandler() {}

    public static void tick() {
        if (Minecraft.getInstance().screen instanceof StockKeeperRequestScreenInvoker invoker) {
            invoker.createimp$pollWorkWarehouseAvailability();
        }
    }
}