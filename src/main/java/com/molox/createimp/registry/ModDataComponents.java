package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.item.NetworkSelectedState;
import com.molox.createimp.item.TemplateOrderTarget;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateImp.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<NetworkLabel>>> NETWORK_MANAGER_LABELS =
            DATA_COMPONENTS.register("network_manager_labels", () ->
                    DataComponentType.<List<NetworkLabel>>builder()
                            .persistent(NetworkLabel.CODEC.listOf())
                            .networkSynchronized(NetworkLabel.STREAM_CODEC.apply(ByteBufCodecs.list()))
                            .build()
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NetworkSelectedState>> NETWORK_SELECTED_STATE =
            DATA_COMPONENTS.register("network_selected_state", () ->
                    DataComponentType.<NetworkSelectedState>builder()
                            .persistent(NetworkSelectedState.CODEC)
                            .build()
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> NETWORK_MANAGER_SEARCH =
            DATA_COMPONENTS.register("network_manager_search", () ->
                    DataComponentType.<String>builder()
                            .persistent(com.mojang.serialization.Codec.STRING)
                            .build()
            );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TemplateOrderTarget>> TEMPLATE_ORDER_TARGET =
            DATA_COMPONENTS.register("template_order_target", () ->
                    DataComponentType.<TemplateOrderTarget>builder()
                            .persistent(TemplateOrderTarget.CODEC)
                            .networkSynchronized(TemplateOrderTarget.STREAM_CODEC)
                            .build()
            );

    /**
     * 只挂在 {@link com.molox.createimp.item.TemplateFluidTokenItem} 上，记录这个
     * 模板令牌代表的是哪一种流体，供客户端渲染、名字显示使用。
     * <p>
     * 【重要】这里的值类型不能直接用 {@code net.neoforged.neoforge.fluids.FluidStack}
     * 本身——已经通过实机崩溃日志确认过，NeoForge 要求任何数据组件的值类型
     * 必须自己实现 {@code equals}/{@code hashCode}（且不可变），
     * {@code FluidStack} 不满足这一点，直接用会在 {@code stack.set(...)} 时
     * 抛出 {@code IllegalArgumentException}。这里改用
     * {@link com.molox.createimp.item.TemplateFluidContent}，一个手写了
     * {@code equals}/{@code hashCode} 的包装类型（照抄流体包裹自己
     * {@code FluidTankContent} 的做法）。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.molox.createimp.item.TemplateFluidContent>> TEMPLATE_FLUID_CONTENT =
            DATA_COMPONENTS.register("template_fluid_content", () ->
                    DataComponentType.<com.molox.createimp.item.TemplateFluidContent>builder()
                            .persistent(com.molox.createimp.item.TemplateFluidContent.CODEC)
                            .networkSynchronized(com.molox.createimp.item.TemplateFluidContent.STREAM_CODEC)
                            .build()
            );
}