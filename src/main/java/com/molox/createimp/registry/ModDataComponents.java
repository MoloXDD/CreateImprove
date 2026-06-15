package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkLabel;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.network.codec.ByteBufCodecs;

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
}