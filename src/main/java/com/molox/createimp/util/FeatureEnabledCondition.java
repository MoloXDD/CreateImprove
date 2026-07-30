package com.molox.createimp.util;

import com.molox.createimp.CreateImp;
import com.molox.createimp.CreateImpConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * 配方JSON里的{@code neoforge:conditions}条件：按功能名读取
 * {@link com.molox.createimp.CreateImpConfig.FunctionConfig}对应的开关，
 * 关闭时这个条件在配方加载阶段就判定为false，配方从一开始就不会被收进
 * 配方管理器——JEI、原版工作台、其它模组读取配方管理器的任何功能都会认为
 * 这个配方压根不存在。判断只在配方加载（进入世界/{@code /reload}）时跑一次，
 * 所以开关的改动要重进世界或重启游戏才会体现在配方上。
 */
public record FeatureEnabledCondition(String feature) implements ICondition {

    public static final MapCodec<FeatureEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(
            builder -> builder
                    .group(Codec.STRING.fieldOf("feature").forGetter(FeatureEnabledCondition::feature))
                    .apply(builder, FeatureEnabledCondition::new));

    @Override
    public boolean test(IContext context) {
        CreateImpConfig.FunctionConfig.ItemToggles config = CreateImp.getConfig().functionConfig.itemToggles;
        return switch (feature) {
            case "andesite_scrap_bucket" -> config.andesiteScrapBucketEnabled;
            case "brass_scrap_bucket" -> config.brassScrapBucketEnabled;
            case "network_manager" -> config.networkManagerEnabled;
            case "labeled_redstone_link" -> config.labeledRedstoneLinkEnabled;
            case "batch_crafting" -> config.batchCraftingEnabled;
            case "template_system" -> config.templateSystemEnabled;
            case "redstone_link_router" -> config.redstoneLinkRouterEnabled;
            default -> true;
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "createimp_feature_enabled(\"" + this.feature + "\")";
    }
}