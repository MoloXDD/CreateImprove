package com.molox.createimp.util;

/**
 * 让材料窗口在玩家点击确认键时，能从外部重新触发一次原本仓管界面的
 * 真实发送逻辑（{@code StockKeeperRequestScreen.sendIt()}），复用其中
 * 已有的打包/编程红石请求器发送流程，以及发送成功后的动画和音效反馈，
 * 不需要另外发明一套。
 * <p>
 * 注意：这个接口不能放在 {@code com.molox.createimp.mixin} 包下——那个包
 * 在 mixins.json 里被声明为 Mixin 专属包，其中的类不允许被外部代码直接
 * 引用/加载，否则会导致其他模组在加载时报
 * {@code IllegalClassLoadError}。
 */
public interface StockKeeperRequestScreenInvoker {

    void createimp$invokeSendIt();

    /**
     * 材料窗口检测到对应模板链已经失效（比如仪表被拆除或所在区块卸载）时，
     * 从外部调用这个方法清空仓管界面的请求栏（含普通物品和模板），
     * 而不是像取消键那样保留请求栏。
     */
    void createimp$clearRequestBar();

    /**
     * 供 {@link com.molox.createimp.client.WorkWarehouseAvailabilityPollHandler}
     * 每 tick 调用：如果当前请求栏含有模板，按内部节奏定期向服务端发起一次
     * "这个频率下有多少可用工作仓库"的查询，查询结果异步更新到
     * {@link com.molox.createimp.client.ClientWorkWarehouseAvailabilityCache}，
     * 供确认键是否可点、悬浮提示读取。请求栏没有模板时什么都不做。
     */
    void createimp$pollWorkWarehouseAvailability();
}