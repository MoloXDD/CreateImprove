package com.molox.createimp.block.brass_scrap_bucket;

import com.molox.createimp.CreateImp;
import com.molox.createimp.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public class BrassScrapBucketBlockEntity extends BlockEntity {

    private static final int MAX_NUGGETS = 64;
    private static final ResourceLocation EXP_NUGGET_ID = ResourceLocation.fromNamespaceAndPath("create", "experience_nugget");

    private int itemFill = 0;
    private int fluidFill = 0;
    private int nuggetCount = 0;

    public int keepAmount = -1;
    public boolean keepInStacks = false;

    public static final int ATTACH_NONE = 0;
    public static final int ATTACH_ITEM = 1;
    public static final int ATTACH_FLUID = 2;

    public BrassScrapBucketBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.BRASS_SCRAP_BUCKET.get(), pos, state);
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
        BlockEntity be = level.getBlockEntity(above);
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("itemFill", itemFill);
        tag.putInt("fluidFill", fluidFill);
        tag.putInt("nuggetCount", nuggetCount);
        tag.putInt("keepAmount", keepAmount);
        tag.putBoolean("keepInStacks", keepInStacks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0; }
    };

    public final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
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