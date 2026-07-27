package com.molox.createimp.compat.extragauges;

import net.neoforged.fml.ModList;

/**
 * 额外仪表（Extra Gauges）模组是否已加载的判断入口。
 * <p>
 * 本类是本模组与额外仪表之间"只兼容、不依赖"的唯一开关：本模组不引用额外
 * 仪表的任何一个类，模板仪表里所有"超过 3×3 动力合成配方"相关的代码都必须
 * 先经过 {@link #isLoaded()} 判断为真才会生效——未安装额外仪表时，模板仪表
 * 的行为与本次改动之前完全一致，不会因为缺少这个可选依赖而报错，也不会
 * 意外触发任何相关代码路径。
 */
public final class ExtraGaugesCompat {

    public static final String MOD_ID = "extra_gauges";

    private ExtraGaugesCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}