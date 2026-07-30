package com.molox.createimp.block.redstone_link_router;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 路由器地图上一个槽位的持久化数据。{@code type} 为 {@link #EMPTY_TYPE} 时表示这个
 * 槽位当前是空位，其余字段一律取默认值、没有实际意义——用一个字符串哨兵值表示"空"，
 * 而不是用 Optional 包一层，是为了让整行可以直接用一个普通的定长 List 编解码，不需要
 * 额外处理"列表元素可以为 null/缺失"这种情况。
 * <p>
 * {@code itemSlot1}/{@code itemSlot2} 是给物品终端预留的两个带顺序的物品数据位，
 * {@code labelText} 是给文本终端预留的文本数据位；这两种终端具体怎么使用这些数据位
 * 目前还没有实现，这里只负责把数据完整保存下来，避免关闭/重新打开界面时丢失。
 */
public record RedstoneLinkRouterComponentData(
        String type,
        boolean notMarked,
        ItemStack itemSlot1,
        ItemStack itemSlot2,
        String labelText,
        List<RedstoneLinkRouterConnectionRef> inputConnections,
        List<RedstoneLinkRouterConnectionRef> outputConnections
) {

    public static final String EMPTY_TYPE = "EMPTY";

    public static final RedstoneLinkRouterComponentData EMPTY = new RedstoneLinkRouterComponentData(
            EMPTY_TYPE, false, ItemStack.EMPTY, ItemStack.EMPTY, "", List.of(), List.of());

    public static final Codec<RedstoneLinkRouterComponentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(RedstoneLinkRouterComponentData::type),
            Codec.BOOL.fieldOf("not_marked").forGetter(RedstoneLinkRouterComponentData::notMarked),
            ItemStack.OPTIONAL_CODEC.fieldOf("item_slot_1").forGetter(RedstoneLinkRouterComponentData::itemSlot1),
            ItemStack.OPTIONAL_CODEC.fieldOf("item_slot_2").forGetter(RedstoneLinkRouterComponentData::itemSlot2),
            Codec.STRING.fieldOf("label_text").forGetter(RedstoneLinkRouterComponentData::labelText),
            RedstoneLinkRouterConnectionRef.CODEC.listOf().fieldOf("input_connections").forGetter(RedstoneLinkRouterComponentData::inputConnections),
            RedstoneLinkRouterConnectionRef.CODEC.listOf().fieldOf("output_connections").forGetter(RedstoneLinkRouterComponentData::outputConnections)
    ).apply(instance, RedstoneLinkRouterComponentData::new));
}