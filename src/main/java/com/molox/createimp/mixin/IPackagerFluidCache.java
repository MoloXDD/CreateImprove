package com.molox.createimp.util;

import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * {@code MixinPackagerFluidCache} 实现的接口，供 {@code WorkWarehouseBlockEntity}
 * 跨方块实体直接调用，把打包机身上"累计流体缓存"这几个操作暴露出来。
 * <p>
 * 【为什么放在 util 包，不放在 mixin 包】Mixin 配置文件把
 * {@code com.molox.createimp.mixin} 整个声明成了"mixin 专属包"，这个包下的
 * 任何类都不能被外部代码直接引用/加载（哪怕只是一个普通接口，不是
 * {@code @Mixin} 类本身），实机验证过放错包会直接导致游戏在模组加载阶段
 * 崩溃。项目里已有的同类接口（{@code StockKeeperRequestScreenInvoker}）也是
 * 放在 {@code util} 包下，这里跟随同一个已经验证过可行的约定。
 * <p>
 * 这几个方法只应该被我们自己模组内部调用——不经过任何标准 capability
 * 通道，外部世界（流体管道、其他物流方案）不知道这个接口存在，天然访问
 * 不到。
 */
public interface IPackagerFluidCache {

    /** 缓存里这种流体（按种类+数据组件匹配）当前一共有多少。 */
    int createimp$getCachedFluidAmount(FluidStack sample);

    /** 从缓存里取出最多这么多这种流体，实际不够就按实际数量全部取出。 */
    FluidStack createimp$extractCachedFluid(FluidStack sample, int amount);

    /** 加入一份流体到缓存，返回没能存下、需要调用方自行处理的剩余数量。 */
    int createimp$addCachedFluid(FluidStack toAdd);

    /** 缓存里当前所有非空流体种类的快照（每种一条）。 */
    List<FluidStack> createimp$nonEmptyCachedFluids();

    boolean createimp$isCachedFluidEmpty();
}