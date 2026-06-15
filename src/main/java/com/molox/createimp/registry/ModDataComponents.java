package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkLabel;
import com.molox.createimp.item.NetworkSelectedState;
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

    // 仅 persistent，不 networkSynchronized
    // 客户端通过 SetNetworkSelectionPacket 直接获得状态，无需物品同步
    // 去掉 networkSynchronized 可防止每次物品栏操作触发换手动画
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NetworkSelectedState>> NETWORK_SELECTED_STATE =
            DATA_COMPONENTS.register("network_selected_state", () ->
                    DataComponentType.<NetworkSelectedState>builder()
                            .persistent(NetworkSelectedState.CODEC)
                            .build()
            );
}