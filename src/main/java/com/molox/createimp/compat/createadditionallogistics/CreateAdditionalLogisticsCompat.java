package com.molox.createimp.compat.createadditionallogistics;

import net.neoforged.fml.ModList;

/**
 * 承诺上限（Create: Additional Logistics）模组是否已加载的判断入口。
 * <p>
 * 本类是本模组与该附属模组之间“只兼容、不依赖”的唯一开关：所有涉及该模组
 * 具体接口（{@code IPromiseLimit} 等）的代码都必须封装在
 * {@code com.molox.createimp.compat.createadditionallogistics} 包下的类里，
 * 并且必须先经过 {@link #isLoaded()} 判断为真才能调用——这样未安装该模组时，
 * 这些类永远不会被真正触发加载，不会因为缺少这个可选依赖而报错。
 */
public final class CreateAdditionalLogisticsCompat {

    public static final String MOD_ID = "createadditionallogistics";

    private CreateAdditionalLogisticsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}