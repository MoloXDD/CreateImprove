package com.molox.createimp.compat.fluidlogistics;

import net.neoforged.fml.ModList;

/**
 * 流体包裹（FluidLogistics）模组是否已加载的判断入口。
 * <p>
 * 本类是本模组与流体包裹之间“只兼容、不依赖”的唯一开关：所有涉及流体包裹
 * 具体类（{@code CompressedTankItem}、{@code FluidStack} 等）的代码都必须
 * 封装在 {@code com.molox.createimp.compat.fluidlogistics} 包下的类里，并且
 * 必须先经过 {@link #isLoaded()} 判断为真才能调用——这样未安装流体包裹时，
 * 这些类永远不会被触发加载，不会因为缺少这个可选依赖而报错。
 */
public final class FluidLogisticsCompat {

    public static final String MOD_ID = "fluidlogistics";

    private FluidLogisticsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}