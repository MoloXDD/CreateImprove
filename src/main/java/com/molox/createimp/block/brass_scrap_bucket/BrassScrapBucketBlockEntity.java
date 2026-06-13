package com.molox.createimp.block.brass_scrap_bucket;

import com.molox.createimp.CreateImp;
import com.molox.createimp.CreateImpConfig;
import com.simibubi.create.content.logistics.chute.SmartChuteFilterSlotPositioning;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

public class BrassScrapBucketBlockEntity extends SmartBlockEntity {

    private static final int MAX_NUGGETS = 64;
    private static final ResourceLocation EXP_NUGGET_ID = ResourceLocation.fromNamespaceAndPath("create", "experience_nugget");

    private int itemFill = 0;
    private int fluidFill = 0;
    private int nuggetCount = 0;

    public int keepAmount = -1;
    public boolean keepInStacks = false;

    private int itemTickCounter = 0;
    private int fluidTickCounter = 0;

    public FilteringBehaviour filtering;

    public static final int ATTACH_NONE = 0;
    public static final int ATTACH_ITEM = 1;
    public static final int ATTACH_FLUID = 2;

    public BrassScrapBucketBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filtering = new FilteringBehaviour(this, new SmartChuteFilterSlotPositioning()).forFluids();
        filtering.customLabel = net.minecraft.network.chat.Component.translatable("block.createimp.brass_scrap_bucket.filter_label");
        behaviours.add(filtering);
    }

    private Item getExpNuggetItem() {
        return BuiltInRegistries.ITEM.get(EXP_NUGGET_ID);
    }

    public int getAttachType() {
        if (level == null) return ATTACH_NONE;
        BlockPos above = worldPosition.above();
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.is(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "scrap_bucket")))) {
            return ATTACH_NONE;
        }
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(above);
        if (be == null) return ATTACH_NONE;
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, above, Direction.DOWN) != null)
            return ATTACH_ITEM;
        if (level.getCapability(Capabilities.FluidHandler.BLOCK, above, Direction.DOWN) != null)
            return ATTACH_FLUID;
        return ATTACH_NONE;
    }

    public int getAboveMaxItems() {
        if (level == null) return 0;
        BlockPos above = worldPosition.above();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            total += Math.min(64, handler.getSlotLimit(i));
        }
        return total;
    }

    public int getAboveMaxStacks() {
        if (level == null) return 0;
        BlockPos above = worldPosition.above();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        return handler.getSlots();
    }

    public int getAboveMaxFluids() {
        if (level == null) return 0;
        BlockPos above = worldPosition.above();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            total += handler.getTankCapacity(i);
        }
        return total / 1000;
    }

    public int getAboveCurrentItems() {
        if (level == null) return 0;
        BlockPos above = worldPosition.above();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            total += handler.getStackInSlot(i).getCount();
        }
        return total;
    }

    public int getAboveCurrentStacks() {
        if (level == null) return 0;
        BlockPos above = worldPosition.above();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        int occupied = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) occupied++;
        }
        return occupied;
    }

    public int getAboveCurrentFluids() {
        if (level == null) return 0;
        BlockPos above = worldPosition.above();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            total += handler.getFluidInTank(i).getAmount();
        }
        return total / 1000;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;
        if (keepAmount < 0) return;

        CreateImpConfig config = CreateImp.getConfig();
        int attachType = getAttachType();

        if (attachType == ATTACH_ITEM) {
            itemTickCounter++;
            if (itemTickCounter >= config.brassScrapBucket.itemTransferInterval) {
                itemTickCounter = 0;
                tickItemDrain(config);
            }
        } else if (attachType == ATTACH_FLUID) {
            fluidTickCounter++;
            if (fluidTickCounter >= config.brassScrapBucket.fluidTransferInterval) {
                fluidTickCounter = 0;
                tickFluidDrain(config);
            }
        }
    }

    private void tickItemDrain(CreateImpConfig config) {
        if (level == null) return;
        BlockPos above = worldPosition.above();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return;

        if (keepInStacks) {
            // 组模式：数格子数，超出的格子整格销毁
            int occupiedSlots = 0;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (!handler.getStackInSlot(i).isEmpty()) occupiedSlots++;
            }
            int itemsPerStack = Math.max(1, getAboveMaxItems() / getAboveMaxStacks());
            int limitStacks = keepAmount / itemsPerStack;
            if (occupiedSlots <= limitStacks) return;

            int slotsToDestroy = occupiedSlots - limitStacks;
            int transferLimit = config.brassScrapBucket.itemTransferAmount;
            int destroyed = 0;

            for (int i = 0; i < handler.getSlots() && slotsToDestroy > 0; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (!filtering.getFilter().isEmpty() && !filtering.test(stack)) continue;

                int canTake = Math.min(stack.getCount(), transferLimit - destroyed);
                if (canTake <= 0) break;
                ItemStack extracted = handler.extractItem(i, canTake, false);
                destroyed += extracted.getCount();
                if (extracted.getCount() >= stack.getCount()) slotsToDestroy--;
                itemFill += extracted.getCount();
                int itemsPerNugget = CreateImp.getConfig().brassScrapBucket.itemsPerNugget;
                while (itemFill >= itemsPerNugget && nuggetCount < MAX_NUGGETS) {
                    itemFill -= itemsPerNugget;
                    tryProduceNugget();
                }
                if (itemFill >= itemsPerNugget) itemFill = itemFill % itemsPerNugget;
                setChanged();
            }
        } else {
            // 个模式：数总个数
            int currentTotal = 0;
            for (int i = 0; i < handler.getSlots(); i++) {
                currentTotal += handler.getStackInSlot(i).getCount();
            }
            if (currentTotal <= keepAmount) return;

            int toDestroy = Math.min(currentTotal - keepAmount, config.brassScrapBucket.itemTransferAmount);
            int destroyed = 0;

            for (int i = 0; i < handler.getSlots() && destroyed < toDestroy; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (!filtering.getFilter().isEmpty() && !filtering.test(stack)) continue;

                int canTake = Math.min(stack.getCount(), toDestroy - destroyed);
                ItemStack extracted = handler.extractItem(i, canTake, false);
                destroyed += extracted.getCount();
                itemFill += extracted.getCount();
                int itemsPerNugget = CreateImp.getConfig().brassScrapBucket.itemsPerNugget;
                while (itemFill >= itemsPerNugget && nuggetCount < MAX_NUGGETS) {
                    itemFill -= itemsPerNugget;
                    tryProduceNugget();
                }
                if (itemFill >= itemsPerNugget) itemFill = itemFill % itemsPerNugget;
                setChanged();
            }
        }
    }

    private void tickFluidDrain(CreateImpConfig config) {
        if (level == null) return;
        BlockPos above = worldPosition.above();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return;

        // 当前总流体量（mB）
        int currentTotal = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            currentTotal += handler.getFluidInTank(i).getAmount();
        }

        // 限制值转换为 mB
        int limitInMb = keepAmount * 1000;
        if (currentTotal <= limitInMb) return;

        int toDestroy = Math.min(
                currentTotal - limitInMb,
                config.brassScrapBucket.fluidTransferAmount
        );

        int remaining = toDestroy;
        for (int i = 0; i < handler.getTanks() && remaining > 0; i++) {
            net.neoforged.neoforge.fluids.FluidStack inTank = handler.getFluidInTank(i);
            if (inTank.isEmpty()) continue;
            if (!filtering.getFilter().isEmpty() && !filtering.test(inTank)) continue;

            net.neoforged.neoforge.fluids.FluidStack toDrain =
                    new net.neoforged.neoforge.fluids.FluidStack(inTank.getFluid(), remaining);
            net.neoforged.neoforge.fluids.FluidStack drained =
                    handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
            int drainedAmount = drained.getAmount();
            remaining -= drainedAmount;
            fluidFill += drainedAmount;
            int mbPerNugget = CreateImp.getConfig().brassScrapBucket.mbPerNugget;
            while (fluidFill >= mbPerNugget && nuggetCount < MAX_NUGGETS) {
                fluidFill -= mbPerNugget;
                tryProduceNugget();
            }
            if (fluidFill >= mbPerNugget) fluidFill = fluidFill % mbPerNugget;
            setChanged();
        }
    }

    public void resetKeepConfig() {
        keepAmount = -1;
        keepInStacks = false;
        setChanged();
    }

    private void tryProduceNugget() {
        if (nuggetCount < MAX_NUGGETS) {
            nuggetCount++;
            setChanged();
        }
    }

    public int getNuggetCount() {
        return nuggetCount;
    }

    public ItemStack takeAllNuggets() {
        if (nuggetCount <= 0) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(getExpNuggetItem(), nuggetCount);
        nuggetCount = 0;
        setChanged();
        return result;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("itemFill", itemFill);
        tag.putInt("fluidFill", fluidFill);
        tag.putInt("nuggetCount", nuggetCount);
        tag.putInt("keepAmount", keepAmount);
        tag.putBoolean("keepInStacks", keepInStacks);
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        itemFill = tag.getInt("itemFill");
        fluidFill = tag.getInt("fluidFill");
        nuggetCount = tag.getInt("nuggetCount");
        keepAmount = tag.contains("keepAmount") ? tag.getInt("keepAmount") : -1;
        keepInStacks = tag.getBoolean("keepInStacks");
    }

    public final IItemHandler itemHandler = new IItemHandler() {
        @Override public int getSlots() { return 2; }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == 1) {
                if (nuggetCount <= 0) return ItemStack.EMPTY;
                return new ItemStack(getExpNuggetItem(), nuggetCount);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) return stack;
            if (!filtering.getFilter().isEmpty() && !filtering.test(stack)) return stack;
            if (!simulate) {
                int itemsPerNugget = CreateImp.getConfig().brassScrapBucket.itemsPerNugget;
                itemFill += stack.getCount();
                while (itemFill >= itemsPerNugget && nuggetCount < MAX_NUGGETS) {
                    itemFill -= itemsPerNugget;
                    tryProduceNugget();
                }
                if (itemFill >= itemsPerNugget) itemFill = itemFill % itemsPerNugget;
                setChanged();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || nuggetCount <= 0) return ItemStack.EMPTY;
            int extracted = Math.min(amount, nuggetCount);
            if (!simulate) { nuggetCount -= extracted; setChanged(); }
            return new ItemStack(getExpNuggetItem(), extracted);
        }

        @Override public int getSlotLimit(int slot) { return slot == 1 ? MAX_NUGGETS : 64; }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != 0) return false;
            return filtering.getFilter().isEmpty() || filtering.test(stack);
        }
    };

    public final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            if (!filtering.getFilter().isEmpty() && !filtering.test(resource)) return 0;
            if (!action.simulate()) {
                int mbPerNugget = CreateImp.getConfig().brassScrapBucket.mbPerNugget;
                fluidFill += resource.getAmount();
                while (fluidFill >= mbPerNugget && nuggetCount < MAX_NUGGETS) {
                    fluidFill -= mbPerNugget;
                    tryProduceNugget();
                }
                if (fluidFill >= mbPerNugget) fluidFill = fluidFill % mbPerNugget;
                setChanged();
            }
            return resource.getAmount();
        }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };
}