package com.molox.createimp.block.brass_scrap_bucket;

import com.molox.createimp.CreateImp;
import com.molox.createimp.CreateImpConfig;
import com.molox.createimp.block.ScrapBucketBlacklist;
import com.simibubi.create.content.logistics.chute.SmartChuteFilterSlotPositioning;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrassScrapBucketBlockEntity extends SmartBlockEntity {

    // 仅用于旧存档兼容读取：旧格式只存了数量，固定假设为经验颗粒
    private static final ResourceLocation LEGACY_EXP_NUGGET_ID = ResourceLocation.fromNamespaceAndPath("create", "experience_nugget");

    private int itemFill = 0;
    private int fluidFill = 0;
    private final Map<ResourceLocation, Integer> configuredItemFill = new HashMap<>();
    private final Map<ResourceLocation, Integer> configuredFluidFill = new HashMap<>();
    private ItemStack producedStack = ItemStack.EMPTY;

    public int keepAmount = -1;
    public boolean keepInStacks = false;
    public ItemStack filterIcon = ItemStack.EMPTY;

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
        filtering = new FilteringBehaviour(this, new SmartChuteFilterSlotPositioning())
                .forFluids()
                .withCallback(stack -> {
                    ItemStack normalized = stack.copy();
                    if (!normalized.isEmpty()) normalized.setCount(1);
                    filterIcon = normalized;
                    setChanged();
                    sendData();
                });
        filtering.customLabel = net.minecraft.network.chat.Component.translatable("block.createimp.brass_scrap_bucket.filter_label");
        behaviours.add(filtering);
    }

    /**
     * 解析当前配置的黄铜废料桶生产物品。ID 为空、无法解析、或注册表中查无此物品，
     * 均返回 null，代表"不生产"。为静态方法，供客户端 tooltip 复用同一套判定。
     */
    public static Item resolveProduceItem(CreateImpConfig config) {
        String id = config.scrapBucket.brassScrapBucketProduceItem;
        if (id == null || id.isBlank()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) return null;
        if (!BuiltInRegistries.ITEM.containsKey(rl)) return null;
        return BuiltInRegistries.ITEM.get(rl);
    }

    /**
     * 是否应当继续累积产出进度（itemFill/fluidFill）。以下任一情况返回 false，
     * 此时进度条本身也会冻结、不再增长：
     * 1. 配置开关关闭；
     * 2. 配置物品ID解析失败或为空；
     * 3. 产出槽位当前已有物品，且与当前配置物品不一致。
     */
    private boolean canProduceAccumulate() {
        if (!CreateImp.getConfig().scrapBucket.generateExperienceNuggets) return false;
        Item produceItem = resolveProduceItem(CreateImp.getConfig());
        if (produceItem == null) return false;
        return producedStack.isEmpty() || producedStack.is(produceItem);
    }

    private void produceOneUnit(Item produceItem) {
        if (producedStack.isEmpty()) {
            producedStack = new ItemStack(produceItem, 1);
        } else {
            producedStack.grow(1);
        }
    }

    /**
     * 累积物品销毁量并尝试转化为产出物品。供直接输入销毁与维持存量自动漏出销毁
     * 两条路径共用，确保判定逻辑完全一致。
     */
    private void accumulateItemFill(ItemStack destroyedStack) {
        if (!canProduceAccumulate()) return;
        CreateImpConfig config = CreateImp.getConfig();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(destroyedStack.getItem());
        CreateImpConfig.ScrapBucket.ItemProductionEfficiency efficiency = findItemProductionEfficiency(config, itemId);
        int currentFill = efficiency == null ? itemFill : configuredItemFill.getOrDefault(itemId, 0);
        currentFill += destroyedStack.getCount();
        Item produceItem = resolveProduceItem(config);
        int itemsPerNugget = Math.max(1, efficiency == null
                ? config.scrapBucket.itemsPerNugget
                : efficiency.itemsPerNugget);
        int maxStack = produceItem.getDefaultInstance().getMaxStackSize();
        while (currentFill >= itemsPerNugget) {
            int currentCount = producedStack.isEmpty() ? 0 : producedStack.getCount();
            if (currentCount >= maxStack) break;
            currentFill -= itemsPerNugget;
            produceOneUnit(produceItem);
        }
        if (currentFill >= itemsPerNugget) currentFill %= itemsPerNugget;
        if (efficiency == null) {
            itemFill = currentFill;
        } else {
            configuredItemFill.put(itemId, currentFill);
        }
    }

    /**
     * 累积流体销毁量（单位 mB）并尝试转化为产出物品，逻辑与 accumulateItemFill 对称。
     */
    private void accumulateFluidFill(FluidStack destroyedFluid) {
        if (!canProduceAccumulate()) return;
        CreateImpConfig config = CreateImp.getConfig();
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(destroyedFluid.getFluid());
        CreateImpConfig.ScrapBucket.FluidProductionEfficiency efficiency = findFluidProductionEfficiency(config, fluidId);
        int currentFill = efficiency == null ? fluidFill : configuredFluidFill.getOrDefault(fluidId, 0);
        currentFill += destroyedFluid.getAmount();
        Item produceItem = resolveProduceItem(config);
        int mbPerNugget = Math.max(1, efficiency == null
                ? config.scrapBucket.mbPerNugget
                : efficiency.mbPerNugget);
        int maxStack = produceItem.getDefaultInstance().getMaxStackSize();
        while (currentFill >= mbPerNugget) {
            int currentCount = producedStack.isEmpty() ? 0 : producedStack.getCount();
            if (currentCount >= maxStack) break;
            currentFill -= mbPerNugget;
            produceOneUnit(produceItem);
        }
        if (currentFill >= mbPerNugget) currentFill %= mbPerNugget;
        if (efficiency == null) {
            fluidFill = currentFill;
        } else {
            configuredFluidFill.put(fluidId, currentFill);
        }
    }

    private static CreateImpConfig.ScrapBucket.ItemProductionEfficiency findItemProductionEfficiency(
            CreateImpConfig config, ResourceLocation itemId) {
        for (CreateImpConfig.ScrapBucket.ItemProductionEfficiency efficiency
                : config.scrapBucket.itemProductionEfficiencies) {
            ResourceLocation configuredId = ResourceLocation.tryParse(efficiency.itemId == null ? "" : efficiency.itemId.trim());
            if (itemId.equals(configuredId)) return efficiency;
        }
        return null;
    }

    private static CreateImpConfig.ScrapBucket.FluidProductionEfficiency findFluidProductionEfficiency(
            CreateImpConfig config, ResourceLocation fluidId) {
        for (CreateImpConfig.ScrapBucket.FluidProductionEfficiency efficiency
                : config.scrapBucket.fluidProductionEfficiencies) {
            ResourceLocation configuredId = ResourceLocation.tryParse(efficiency.fluidId == null ? "" : efficiency.fluidId.trim());
            if (fluidId.equals(configuredId)) return efficiency;
        }
        return null;
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

    // GUI显示用，单位：桶
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

    // tick内部使用，单位：mB，避免精度损失
    private int getAboveCurrentFluidsMb() {
        if (level == null) return 0;
        BlockPos above = worldPosition.above();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            total += handler.getFluidInTank(i).getAmount();
        }
        return total;
    }

    public int getFilteredCurrentItems() {
        if (level == null) return 0;
        if (filterIcon.isEmpty()) return getAboveCurrentItems();
        BlockPos above = worldPosition.above();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        FilterItemStack fis = FilterItemStack.of(filterIcon);
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && fis.test(level, stack, false)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public int getFilteredCurrentStacks() {
        if (level == null) return 0;
        if (filterIcon.isEmpty()) return getAboveCurrentStacks();
        BlockPos above = worldPosition.above();
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        FilterItemStack fis = FilterItemStack.of(filterIcon);
        int occupied = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && fis.test(level, stack, false)) {
                occupied++;
            }
        }
        return occupied;
    }

    // GUI显示用，单位：桶
    public int getFilteredCurrentFluids() {
        if (level == null) return 0;
        if (filterIcon.isEmpty()) return getAboveCurrentFluids();
        BlockPos above = worldPosition.above();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        FilterItemStack fis = FilterItemStack.of(filterIcon);
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fs = handler.getFluidInTank(i);
            if (!fs.isEmpty() && fis.test(level, fs, false)) {
                total += fs.getAmount();
            }
        }
        return total / 1000;
    }

    // tick内部使用，单位：mB，避免精度损失
    private int getFilteredCurrentFluidsMb() {
        if (level == null) return 0;
        if (filterIcon.isEmpty()) return getAboveCurrentFluidsMb();
        BlockPos above = worldPosition.above();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return 0;
        FilterItemStack fis = FilterItemStack.of(filterIcon);
        int total = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fs = handler.getFluidInTank(i);
            if (!fs.isEmpty() && fis.test(level, fs, false)) {
                total += fs.getAmount();
            }
        }
        return total;
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
            if (itemTickCounter >= config.scrapBucket.itemTransferInterval) {
                itemTickCounter = 0;
                tickItemDrain(config);
            }
        } else if (attachType == ATTACH_FLUID) {
            fluidTickCounter++;
            if (fluidTickCounter >= config.scrapBucket.fluidTransferInterval) {
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

        FilterItemStack fis = filterIcon.isEmpty() ? null : FilterItemStack.of(filterIcon);

        if (keepInStacks) {
            int occupiedSlots = fis != null ? getFilteredCurrentStacks() : getAboveCurrentStacks();
            int itemsPerStack = Math.max(1, getAboveMaxItems() / Math.max(1, getAboveMaxStacks()));
            int limitStacks = keepAmount / itemsPerStack;
            if (occupiedSlots <= limitStacks) return;

            int transferLimit = config.scrapBucket.itemTransferAmount;
            int destroyed = 0;
            int slotsStillToRemove = occupiedSlots - limitStacks;

            for (int i = 0; i < handler.getSlots() && slotsStillToRemove > 0 && destroyed < transferLimit; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (fis != null && !fis.test(level, stack, false)) continue;
                if (ScrapBucketBlacklist.isBlacklisted(stack)) continue;

                int canTake = Math.min(stack.getCount(), transferLimit - destroyed);
                if (canTake <= 0) break;
                ItemStack extracted = handler.extractItem(i, canTake, false);
                if (extracted.getCount() >= stack.getCount()) slotsStillToRemove--;
                destroyed += extracted.getCount();
                accumulateItemFill(extracted);
                setChanged();
            }
        } else {
            int transferLimit = config.scrapBucket.itemTransferAmount;
            int destroyed = 0;

            for (int i = 0; i < handler.getSlots() && destroyed < transferLimit; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (fis != null && !fis.test(level, stack, false)) continue;
                if (ScrapBucketBlacklist.isBlacklisted(stack)) continue;

                int recalcFiltered = fis != null ? getFilteredCurrentItems() : getAboveCurrentItems();
                if (recalcFiltered <= keepAmount) break;

                int excess = recalcFiltered - keepAmount;
                int canTake = Math.min(Math.min(stack.getCount(), excess), transferLimit - destroyed);
                if (canTake <= 0) break;
                ItemStack extracted = handler.extractItem(i, canTake, false);
                destroyed += extracted.getCount();
                accumulateItemFill(extracted);
                setChanged();
            }
        }
    }

    private void tickFluidDrain(CreateImpConfig config) {
        if (level == null) return;
        BlockPos above = worldPosition.above();
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, above, Direction.DOWN);
        if (handler == null) return;

        FilterItemStack fis = filterIcon.isEmpty() ? null : FilterItemStack.of(filterIcon);

        // 使用mB级别方法，避免/1000再*1000的精度损失
        int currentMb = fis != null ? getFilteredCurrentFluidsMb() : getAboveCurrentFluidsMb();
        int limitMb = keepAmount * 1000;
        if (currentMb <= limitMb) return;

        int toDestroy = Math.min(currentMb - limitMb, config.scrapBucket.fluidTransferAmount);
        int remaining = toDestroy;

        for (int i = 0; i < handler.getTanks() && remaining > 0; i++) {
            FluidStack inTank = handler.getFluidInTank(i);
            if (inTank.isEmpty()) continue;
            if (fis != null && !fis.test(level, inTank, false)) continue;
            if (ScrapBucketBlacklist.isBlacklisted(inTank)) continue;

            FluidStack toDrain = new FluidStack(inTank.getFluid(), remaining);
            FluidStack drained = handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
            remaining -= drained.getAmount();
            accumulateFluidFill(drained);
            setChanged();
        }
    }

    public void resetKeepConfig() {
        keepAmount = -1;
        keepInStacks = false;
        setChanged();
    }

    private static void writeConfiguredFill(CompoundTag tag, String key, Map<ResourceLocation, Integer> fills) {
        CompoundTag fillTag = new CompoundTag();
        fills.forEach((id, amount) -> fillTag.putInt(id.toString(), amount));
        tag.put(key, fillTag);
    }

    private static void readConfiguredFill(CompoundTag tag, String key, Map<ResourceLocation, Integer> fills) {
        fills.clear();
        if (!tag.contains(key)) return;
        CompoundTag fillTag = tag.getCompound(key);
        for (String idString : fillTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(idString);
            if (id != null) fills.put(id, fillTag.getInt(idString));
        }
    }

    public int getProducedCount() {
        return producedStack.getCount();
    }

    public ItemStack takeAllProduced() {
        if (producedStack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = producedStack.copy();
        producedStack = ItemStack.EMPTY;
        setChanged();
        return result;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("itemFill", itemFill);
        tag.putInt("fluidFill", fluidFill);
        writeConfiguredFill(tag, "configuredItemFill", configuredItemFill);
        writeConfiguredFill(tag, "configuredFluidFill", configuredFluidFill);
        if (!producedStack.isEmpty()) {
            tag.put("producedStack", producedStack.save(registries, new CompoundTag()));
        }
        tag.putInt("keepAmount", keepAmount);
        tag.putBoolean("keepInStacks", keepInStacks);
        if (!filterIcon.isEmpty()) {
            tag.put("filterIcon", filterIcon.save(registries, new CompoundTag()));
        }
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        itemFill = tag.getInt("itemFill");
        fluidFill = tag.getInt("fluidFill");
        readConfiguredFill(tag, "configuredItemFill", configuredItemFill);
        readConfiguredFill(tag, "configuredFluidFill", configuredFluidFill);
        if (tag.contains("producedStack")) {
            producedStack = ItemStack.parseOptional(registries, tag.getCompound("producedStack"));
        } else if (tag.contains("nuggetCount")) {
            // 旧存档兼容：旧格式只存了经验颗粒数量，按经验颗粒物品重建为 producedStack
            int legacyCount = tag.getInt("nuggetCount");
            producedStack = legacyCount > 0
                    ? new ItemStack(BuiltInRegistries.ITEM.get(LEGACY_EXP_NUGGET_ID), legacyCount)
                    : ItemStack.EMPTY;
        } else {
            producedStack = ItemStack.EMPTY;
        }
        keepAmount = tag.contains("keepAmount") ? tag.getInt("keepAmount") : -1;
        keepInStacks = tag.getBoolean("keepInStacks");
        if (tag.contains("filterIcon")) {
            filterIcon = ItemStack.parseOptional(registries, tag.getCompound("filterIcon"));
        } else {
            filterIcon = ItemStack.EMPTY;
        }
    }

    public final IItemHandler itemHandler = new IItemHandler() {
        @Override public int getSlots() { return 2; }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == 1) {
                return producedStack.isEmpty() ? ItemStack.EMPTY : producedStack.copy();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) return stack;
            if (ScrapBucketBlacklist.isBlacklisted(stack)) return stack;
            if (!filterIcon.isEmpty()) {
                FilterItemStack fis = FilterItemStack.of(filterIcon);
                if (!fis.test(level, stack, false)) return stack;
            }
            if (!simulate) {
                accumulateItemFill(stack);
                setChanged();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || producedStack.isEmpty()) return ItemStack.EMPTY;
            int extracted = Math.min(amount, producedStack.getCount());
            ItemStack result = producedStack.copyWithCount(extracted);
            if (!simulate) {
                producedStack.shrink(extracted);
                setChanged();
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot != 1) return 64;
            if (!producedStack.isEmpty()) return producedStack.getMaxStackSize();
            Item produceItem = resolveProduceItem(CreateImp.getConfig());
            return produceItem != null ? produceItem.getDefaultInstance().getMaxStackSize() : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot != 0) return false;
            if (ScrapBucketBlacklist.isBlacklisted(stack)) return false;
            if (filterIcon.isEmpty()) return true;
            return FilterItemStack.of(filterIcon).test(level, stack, false);
        }
    };

    public final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return !ScrapBucketBlacklist.isBlacklisted(stack); }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            if (ScrapBucketBlacklist.isBlacklisted(resource)) return 0;
            if (!filterIcon.isEmpty()) {
                FilterItemStack fis = FilterItemStack.of(filterIcon);
                if (!fis.test(level, resource, false)) return 0;
            }
            if (!action.simulate()) {
                accumulateFluidFill(resource);
                setChanged();
            }
            return resource.getAmount();
        }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    };
}
