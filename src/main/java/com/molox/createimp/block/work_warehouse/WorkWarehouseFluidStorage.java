package com.molox.createimp.block.work_warehouse;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作仓库自己专用的流体存储：{@link #SLOT_COUNT} 个槽位，每个槽位最多
 * 装一种流体、数量上限就是 {@code int} 能表示的最大值（"用不完"），跟现有
 * {@link WorkWarehouseItemStackHandler} 对物品"无限堆叠"的设计是同一个
 * 思路。
 * <p>
 * 【故意不实现 {@code IFluidHandler}】这份存储从头到尾都不是一个标准的流体
 * capability——不光是"不对外注册"，而是压根没有实现那个接口。这跟物品那边
 * "实现了 {@code IItemHandler} 接口、但从不注册暴露"的做法不一样，是刻意选的
 * 更保守的方式：流体这边完全是本模组自己内部读写的数据结构，从类型上就
 * 排除了被任何标准物流管线（不管是流体包裹自己的流体管道，还是其他任何
 * 支持 {@code IFluidHandler} 的模组）误认成一个可对接目标的可能性,不需要
 * 依赖"注册与否"这一个单点来保证不被外部访问。
 * <p>
 * 不同种类的流体各占一个独立槽位（按种类+数据组件区分，同一种流体多次
 * 加入会累加进同一个槽位，不会分裂成多份）；相同种类但数据组件不同（比如
 * 流体包裹自己可能携带的某些流体专属数据）视为不同种类,各自占用独立槽位。
 */
public class WorkWarehouseFluidStorage {

    public static final int SLOT_COUNT = 100;

    private final WorkWarehouseBlockEntity blockEntity;
    private final List<FluidStack> tanks;

    public WorkWarehouseFluidStorage(WorkWarehouseBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.tanks = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            tanks.add(FluidStack.EMPTY);
        }
    }

    public int getSlots() {
        return tanks.size();
    }

    public FluidStack getFluidInSlot(int slot) {
        return tanks.get(slot).copy();
    }

    /**
     * 查这种流体（按种类+数据组件匹配，忽略数量）当前一共存了多少，跨槽位
     * 累加——理论上同一种流体应该只会占用一个槽位（{@link #addFluid} 每次都
     * 会先找已有的匹配槽位），这里跨全部槽位求和是为了保守起见，不假设
     * 内部实现细节，调用方永远能拿到真实总量。
     */
    public int getAmount(FluidStack sample) {
        if (sample == null || sample.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (FluidStack tank : tanks) {
            if (!tank.isEmpty() && FluidStack.isSameFluidSameComponents(tank, sample)) {
                total += tank.getAmount();
            }
        }
        return total;
    }

    /**
     * 加入一份流体：优先找已有的同种流体槽位累加，找不到就找一个空槽位
     * 新开一份。槽位全部占满且没有匹配的同种流体时，返回加不进去的剩余
     * 数量（正常使用场景下 {@link #SLOT_COUNT}=100 应该足够，加不进去
     * 属于极端情况的兜底）。
     *
     * @return 未能存入、需要调用方自行处理的剩余数量
     */
    public int addFluid(FluidStack toAdd) {
        if (toAdd == null || toAdd.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < tanks.size(); i++) {
            FluidStack tank = tanks.get(i);
            if (!tank.isEmpty() && FluidStack.isSameFluidSameComponents(tank, toAdd)) {
                long merged = (long) tank.getAmount() + (long) toAdd.getAmount();
                int cappedAmount = (int) Math.min(merged, Integer.MAX_VALUE);
                int overflow = (int) Math.max(0, merged - Integer.MAX_VALUE);
                FluidStack newTank = tank.copy();
                newTank.setAmount(cappedAmount);
                tanks.set(i, newTank);
                onChanged();
                return overflow;
            }
        }
        for (int i = 0; i < tanks.size(); i++) {
            if (tanks.get(i).isEmpty()) {
                tanks.set(i, toAdd.copy());
                onChanged();
                return 0;
            }
        }
        return toAdd.getAmount();
    }

    /**
     * 从存储里取出这种流体最多 {@code amount} 这么多（实际库存不够时按实际
     * 库存全部取出），返回真正取到的那一份；不改变传入的 {@code sample}。
     */
    public FluidStack extractFluid(FluidStack sample, int amount) {
        if (sample == null || sample.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        for (int i = 0; i < tanks.size(); i++) {
            FluidStack tank = tanks.get(i);
            if (tank.isEmpty() || !FluidStack.isSameFluidSameComponents(tank, sample)) {
                continue;
            }
            int taken = Math.min(tank.getAmount(), amount);
            FluidStack result = tank.copy();
            result.setAmount(taken);
            int remaining = tank.getAmount() - taken;
            if (remaining <= 0) {
                tanks.set(i, FluidStack.EMPTY);
            } else {
                FluidStack newTank = tank.copy();
                newTank.setAmount(remaining);
                tanks.set(i, newTank);
            }
            onChanged();
            return result;
        }
        return FluidStack.EMPTY;
    }

    public boolean isEmpty() {
        for (FluidStack tank : tanks) {
            if (!tank.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 清空全部流体存量——卸载流体包裹时按设计要求整体清空使用。 */
    public void clear() {
        boolean hadAny = !isEmpty();
        for (int i = 0; i < tanks.size(); i++) {
            tanks.set(i, FluidStack.EMPTY);
        }
        if (hadAny) {
            onChanged();
        }
    }

    /** 存储里当前所有非空的流体种类快照（每种一条，数量为该种类当前总量）。 */
    public List<FluidStack> nonEmptyContents() {
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack tank : tanks) {
            if (!tank.isEmpty()) {
                result.add(tank.copy());
            }
        }
        return result;
    }

    private void onChanged() {
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (int i = 0; i < tanks.size(); i++) {
            FluidStack tank = tanks.get(i);
            if (tank.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            FluidStack.CODEC.encodeStart(provider.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tank)
                    .resultOrPartial(error -> {
                    })
                    .ifPresent(encoded -> entry.put("Fluid", encoded));
            list.add(entry);
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("Tanks", list);
        return nbt;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        for (int i = 0; i < tanks.size(); i++) {
            tanks.set(i, FluidStack.EMPTY);
        }
        ListTag list = nbt.getList("Tanks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= tanks.size() || !entry.contains("Fluid")) {
                continue;
            }
            FluidStack.CODEC.parse(provider.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), entry.get("Fluid"))
                    .resultOrPartial(error -> {
                    })
                    .ifPresent(fluid -> tanks.set(slot, fluid));
        }
    }

    public WorkWarehouseFluidStorage copy() {
        WorkWarehouseFluidStorage clone = new WorkWarehouseFluidStorage(null);
        for (int i = 0; i < tanks.size(); i++) {
            clone.tanks.set(i, tanks.get(i).copy());
        }
        return clone;
    }
}