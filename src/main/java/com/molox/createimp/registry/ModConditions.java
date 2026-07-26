package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import com.molox.createimp.util.FeatureEnabledCondition;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModConditions {

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, CreateImp.MODID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<FeatureEnabledCondition>> FEATURE_ENABLED =
            CONDITION_SERIALIZERS.register("feature_enabled", () -> FeatureEnabledCondition.CODEC);
}