package com.molox.createimp.compat.createadditionallogistics;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import dev.khloeleclair.create.additionallogistics.common.utilities.IPromiseLimit;

/**
 * 本类集中封装所有对承诺上限（Create: Additional Logistics）具体接口
 * （{@code IPromiseLimit}）的直接引用。
 * <p>
 * 【重要】本类里的任何方法都只允许在调用方已经确认
 * {@link CreateAdditionalLogisticsCompat#isLoaded()} 为真之后才能调用——本类
 * 本身不做这个判断，一旦某个方法被真正调用而该模组未安装，对应的类会在
 * 类加载阶段直接抛出 {@link NoClassDefFoundError}。
 * <p>
 * 承诺上限模组是通过 Mixin 把 {@code IPromiseLimit} 接口加到
 * {@link FactoryPanelBehaviour} 自己身上的，因此这里直接对同一个仪表实例做
 * instanceof / 强转即可，不需要额外的方块位置或槽位信息。
 */
public final class FactoryPanelPromiseLimitHelper {

    private FactoryPanelPromiseLimitHelper() {
    }

    /**
     * 该仪表当前是否设置了有效的承诺上限。承诺上限模组自身的配置开关
     * （{@code enablePromiseLimits}）以及"是否设置了非 -1 的具体数值"这两层
     * 判断都由 {@code IPromiseLimit#hasCALPromiseLimit()} 内部完成，本方法
     * 直接转发其结果。
     */
    public static boolean hasPromiseLimit(FactoryPanelBehaviour behaviour) {
        return behaviour instanceof IPromiseLimit ipl && ipl.hasCALPromiseLimit();
    }

    /**
     * 取出该仪表设置的承诺上限原始数值，单位是"批次数"（配方模式下，承诺
     * 上限模组自己的界面显示时会再乘以单批产出数量才是玩家看到的物品总量，
     * 服务端判定逻辑同样会乘以 recipeOutput 换算成物品数量单位后再与网络
     * 承诺量比较）。调用前必须先确认 {@link #hasPromiseLimit} 为真。
     */
    public static int getPromiseLimitBatches(FactoryPanelBehaviour behaviour) {
        return ((IPromiseLimit) behaviour).getCALPromiseLimit();
    }
}