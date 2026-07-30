package com.molox.createimp.block.redstone_link_router;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** 持久化用的连接引用：指向地图里某一行、某一列的模块。 */
public record RedstoneLinkRouterConnectionRef(int rowIndex, int slotIndex) {

    public static final Codec<RedstoneLinkRouterConnectionRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("row").forGetter(RedstoneLinkRouterConnectionRef::rowIndex),
            Codec.INT.fieldOf("slot").forGetter(RedstoneLinkRouterConnectionRef::slotIndex)
    ).apply(instance, RedstoneLinkRouterConnectionRef::new));
}