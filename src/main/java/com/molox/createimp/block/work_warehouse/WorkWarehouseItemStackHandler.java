package com.molox.createimp.block.work_warehouse;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class WorkWarehouseItemStackHandler extends ItemStackHandler {

    public static final int SLOT_COUNT = 100;

    private final WorkWarehouseBlockEntity blockEntity;

    public WorkWarehouseItemStackHandler(WorkWarehouseBlockEntity blockEntity) {
        super(SLOT_COUNT);
        this.blockEntity = blockEntity;
    }

    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        return stack.getMaxStackSize() > 1 ? Integer.MAX_VALUE : stack.getMaxStackSize();
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }

    /**
     * 这个存储的单格允许无限堆叠（{@link #getStackLimit}
     * 对可堆叠物品直接放开到 {@code Integer.MAX_VALUE}），但原版
     * {@code ItemStackHandler} 默认的序列化写法是直接调用
     * {@code ItemStack.save(...)}——而原版 {@code ItemStack} 的保存格式对
     * 数量字段做了 {@code [1, 99]} 的范围校验，一旦某一格堆到 100 个以上，
     * 存档（自动保存、区块卸载）时就会直接抛异常，导致这整个方块实体的
     * 数据完全写不进存档（服务端日志会报 "It will not persist"，本质上是
     * 数据静默丢失）。
     * <p>
     * 这里重写序列化/反序列化：把物品本身按数量 1 保存（永远不会触碰到
     * 那个 99 的上限），真实数量单独存成一个不受这个限制的普通 int 字段。
     */
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        ListTag nbtTagList = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                itemTag.putInt("RealCount", stack.getCount());
                nbtTagList.add(stack.copyWithCount(1).save(provider, itemTag));
            }
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("Items", nbtTagList);
        nbt.putInt("Size", stacks.size());
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        setSize(nbt.contains("Size", Tag.TAG_INT) ? nbt.getInt("Size") : stacks.size());
        ListTag tagList = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            CompoundTag itemTags = tagList.getCompound(i);
            int slot = itemTags.getInt("Slot");
            if (slot >= 0 && slot < stacks.size()) {
                ItemStack.parse(provider, itemTags).ifPresent(stack -> {
                    int realCount = itemTags.contains("RealCount", Tag.TAG_INT) ? itemTags.getInt("RealCount") : stack.getCount();
                    stacks.set(slot, stack.copyWithCount(Math.max(1, realCount)));
                });
            }
        }
        onLoad();
    }

    public WorkWarehouseItemStackHandler copy() {
        WorkWarehouseItemStackHandler clone = new WorkWarehouseItemStackHandler(null);
        for (int i = 0; i < getSlots(); i++) {
            clone.setStackInSlot(i, getStackInSlot(i).copy());
        }
        return clone;
    }
}