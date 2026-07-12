package com.molox.createimp.block.batch_mechanical_crafter;

import com.molox.createimp.registry.ModBlockEntityTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.item.SmartInventory;
import net.createmod.catnip.math.BlockFace;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BatchMechanicalCrafterBlockEntity extends KineticBlockEntity {

    public Inventory inventory;
    public BatchRecipeGridHandler.GroupedItems groupedItems = new BatchRecipeGridHandler.GroupedItems();
    public BatchConnectedInputHandler.ConnectedInput input = new BatchConnectedInputHandler.ConnectedInput();
    @Nullable
    protected IItemHandler invCap;
    public boolean reRender;
    public Phase phase;
    public int countDown;
    public boolean covered;
    public boolean wasPoweredBefore;
    public BatchRecipeGridHandler.GroupedItems groupedItemsBeforeCraft;
    private InvManipulationBehaviour inserting;
    private EdgeInteractionBehaviour connectivity;
    private ItemStack scriptedResult = ItemStack.EMPTY;

    public int packageProgressOrderId = -1;
    public int packageProgressEntryIndex = 0;
    public int packageProgressEntryRemaining = -1;

    // 记录"包裹已经把材料按pattern分配完毕，但当时合成器没有动力（getSpeed()==0），
    // checkCompletedRecipe(true)因此被跳过、没能真正启动合成"这一待处理状态。
    // 等待动力恢复（onSpeedChanged检测到0->非0）时补一次强制启动尝试，
    // 避免必须手动通一次红石信号才能让已就绪的材料开始合成。
    public boolean pendingForcedStart = false;

    private boolean tickedOnce = false;


    public BatchMechanicalCrafterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.setLazyTickRate(20);
        this.phase = Phase.IDLE;
        this.groupedItemsBeforeCraft = new BatchRecipeGridHandler.GroupedItems();
        this.inventory = new Inventory(this);
        this.wasPoweredBefore = true;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event,
                                            BlockEntityType<BatchMechanicalCrafterBlockEntity> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type,
                (be, context) -> be.getInvCapability());
    }

    protected IItemHandler getInvCapability() {
        if (this.invCap == null)
            this.invCap = this.input.getItemHandler(this.getLevel(), this.getBlockPos());
        return this.invCap;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        this.inserting = new InvManipulationBehaviour((SmartBlockEntity) this, this::getTargetFace);
        this.connectivity = new EdgeInteractionBehaviour((SmartBlockEntity) this,
                BatchConnectedInputHandler::toggleConnection)
                .connectivity(BatchConnectedInputHandler::shouldConnect)
                .require(item -> item.builtInRegistryHolder().is(
                        net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH));
        behaviours.add(this.inserting);
        behaviours.add(this.connectivity);
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (previousSpeed == 0f && this.getSpeed() != 0f && this.pendingForcedStart
                && this.level != null && !this.level.isClientSide()) {
            this.pendingForcedStart = false;
            this.checkCompletedRecipe(true);
        }
    }

    public void blockChanged() {
        this.removeBehaviour(InvManipulationBehaviour.TYPE);
        this.inserting = new InvManipulationBehaviour((SmartBlockEntity) this, this::getTargetFace);
        this.attachBehaviourLate(this.inserting);
    }

    public BlockFace getTargetFace(Level world, BlockPos pos, BlockState state) {
        return new BlockFace(pos, BatchMechanicalCrafterBlock.getTargetDirection(state));
    }

    public Direction getTargetDirection() {
        return BatchMechanicalCrafterBlock.getTargetDirection(this.getBlockState());
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        if (this.input == null) return;
        CompoundTag inputNBT = new CompoundTag();
        this.input.write(inputNBT);
        tag.put("ConnectedInput", inputNBT);
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("Inventory", this.inventory.serializeNBT(registries));
        CompoundTag inputNBT = new CompoundTag();
        this.input.write(inputNBT);
        compound.put("ConnectedInput", inputNBT);
        CompoundTag groupedItemsNBT = new CompoundTag();
        this.groupedItems.write(groupedItemsNBT, registries);
        compound.put("GroupedItems", groupedItemsNBT);
        compound.putString("Phase", this.phase.name());
        compound.putInt("CountDown", this.countDown);
        compound.putBoolean("Cover", this.covered);
        compound.putInt("PkgProgressOrderId", this.packageProgressOrderId);
        compound.putInt("PkgProgressEntryIndex", this.packageProgressEntryIndex);
        compound.putInt("PkgProgressEntryRemaining", this.packageProgressEntryRemaining);
        compound.putBoolean("PendingForcedStart", this.pendingForcedStart);
        super.write(compound, registries, clientPacket);
        if (clientPacket && this.reRender) {
            compound.putBoolean("Redraw", true);
            this.reRender = false;
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        Phase phaseBefore = this.phase;
        BatchRecipeGridHandler.GroupedItems before = this.groupedItems;
        this.inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        this.input.read(compound.getCompound("ConnectedInput"));
        this.groupedItems = BatchRecipeGridHandler.GroupedItems.read(
                compound.getCompound("GroupedItems"), registries);
        this.phase = Phase.IDLE;
        String name = compound.getString("Phase");
        for (Phase phase : Phase.values()) {
            if (!phase.name().equals(name)) continue;
            this.phase = phase;
        }
        this.countDown = compound.getInt("CountDown");
        this.covered = compound.getBoolean("Cover");
        this.packageProgressOrderId = compound.contains("PkgProgressOrderId") ? compound.getInt("PkgProgressOrderId") : -1;
        this.packageProgressEntryIndex = compound.getInt("PkgProgressEntryIndex");
        this.packageProgressEntryRemaining = compound.contains("PkgProgressEntryRemaining") ? compound.getInt("PkgProgressEntryRemaining") : -1;
        this.pendingForcedStart = compound.getBoolean("PendingForcedStart");
        super.read(compound, registries, clientPacket);
        if (!clientPacket) return;
        if (compound.contains("Redraw"))
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 16);
        if (phaseBefore != this.phase && this.phase == Phase.CRAFTING)
            this.groupedItemsBeforeCraft = before;
        if (phaseBefore == Phase.EXPORTING && this.phase == Phase.WAITING) {
            if (before.onlyEmptyItems()) return;
            Direction facing = this.getBlockState().getValue(BatchMechanicalCrafterBlock.HORIZONTAL_FACING);
            Vec3 vec = Vec3.atLowerCornerOf((Vec3i) facing.getNormal()).scale(0.75)
                    .add(VecHelper.getCenterOf((Vec3i) this.worldPosition));
            Direction targetDirection = BatchMechanicalCrafterBlock.getTargetDirection(this.getBlockState());
            vec = vec.add(Vec3.atLowerCornerOf((Vec3i) targetDirection.getNormal()).scale(1.0));
            this.level.addParticle(ParticleTypes.CRIT, vec.x, vec.y, vec.z, 0, 0, 0);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        this.invalidateCapabilities();
    }

    public int getCountDownSpeed() {
        if (this.getSpeed() == 0f) return 0;
        return Mth.clamp((int) Math.abs(this.getSpeed()), 4, 250);
    }

    @Override
    public void tick() {
        super.tick();
        this.tickedOnce = true;
        if (this.phase == Phase.ACCEPTING) return;
        boolean onClient = this.level.isClientSide;
        boolean runLogic = !onClient || this.isVirtual();

        if (this.wasPoweredBefore != this.level.hasNeighborSignal(this.worldPosition)) {
            this.wasPoweredBefore = this.level.hasNeighborSignal(this.worldPosition);
            if (this.wasPoweredBefore) {
                if (!runLogic) return;
                this.checkCompletedRecipe(true);
            }
        }

        if (this.phase == Phase.ASSEMBLING) {
            this.countDown -= this.getCountDownSpeed();
            if (this.countDown < 0) {
                this.countDown = 0;
                if (!runLogic) return;
                if (BatchRecipeGridHandler.getTargetingCrafter(this) != null) {
                    this.phase = Phase.EXPORTING;
                    this.countDown = this.groupedItems.onlyEmptyItems() ? 0 : 1000;
                    this.sendData();
                    return;
                }
                if (this.isVirtual()) {
                    ItemStack result = this.scriptedResult;
                    if (result != null) {
                        if (this.isVirtual())
                            this.groupedItemsBeforeCraft = this.groupedItems;
                        this.groupedItems = new BatchRecipeGridHandler.GroupedItems(result);
                        this.phase = Phase.CRAFTING;
                        this.countDown = 2000;
                        this.sendData();
                        return;
                    }
                    this.ejectWholeGrid();
                    return;
                }
                BatchRecipeGridHandler.BatchCraftResult batchResult =
                        BatchRecipeGridHandler.tryToApplyRecipeBatch(this.level, this.groupedItems);
                if (batchResult != null) {
                    this.groupedItemsBeforeCraft = this.groupedItems;
                    this.groupedItems = batchResult.outputItems;
                    this.phase = Phase.CRAFTING;
                    this.countDown = 2000;
                    this.sendData();
                    return;
                }
                this.ejectWholeGrid();
                return;
            }
        }

        if (this.phase == Phase.EXPORTING) {
            this.countDown -= this.getCountDownSpeed();
            if (this.countDown < 0) {
                this.countDown = 0;
                if (!runLogic) return;
                BatchMechanicalCrafterBlockEntity targetingCrafter =
                        BatchRecipeGridHandler.getTargetingCrafter(this);
                if (targetingCrafter == null) {
                    this.ejectWholeGrid();
                    return;
                }
                boolean empty = this.groupedItems.onlyEmptyItems();
                Pointing pointing = this.getBlockState().getValue(BatchMechanicalCrafterBlock.POINTING);
                this.groupedItems.mergeOnto(targetingCrafter.groupedItems, pointing);
                this.groupedItems = new BatchRecipeGridHandler.GroupedItems();
                float pitch = (float) (targetingCrafter.groupedItems.grid.size()) / 16f + 0.5f;
                if (!empty)
                    AllSoundEvents.CRAFTER_CLICK.playOnServer(this.level, (Vec3i) this.worldPosition, 1f, pitch);
                this.phase = Phase.WAITING;
                this.countDown = 0;
                this.sendData();
                targetingCrafter.continueIfAllPrecedingFinished();
                targetingCrafter.sendData();
                return;
            }
        }

        if (this.phase == Phase.CRAFTING) {
            if (onClient) {
                Direction facing = this.getBlockState().getValue(BatchMechanicalCrafterBlock.HORIZONTAL_FACING);
                float progress = (float) this.countDown / 2000f;
                Vec3 facingVec = Vec3.atLowerCornerOf((Vec3i) facing.getNormal());
                Vec3 vec = facingVec.scale(0.65).add(VecHelper.getCenterOf((Vec3i) this.worldPosition));
                Vec3 offset = VecHelper.offsetRandomly(Vec3.ZERO, this.level.random, 0.125f)
                        .multiply(VecHelper.axisAlingedPlaneOf(facingVec)).normalize()
                        .scale(progress * 0.5f).add(vec);
                if (progress > 0.5f)
                    this.level.addParticle(ParticleTypes.CRIT, offset.x, offset.y, offset.z, 0, 0, 0);
                if (!this.groupedItemsBeforeCraft.grid.isEmpty() && progress < 0.5f
                        && this.groupedItems.grid.containsKey(Pair.of(0, 0))) {
                    ItemStack stack = this.groupedItems.grid.get(Pair.of(0, 0));
                    this.groupedItemsBeforeCraft = new BatchRecipeGridHandler.GroupedItems();
                    for (int i = 0; i < 10; i++) {
                        Vec3 randVec = VecHelper.offsetRandomly(Vec3.ZERO, this.level.random, 0.125f)
                                .multiply(VecHelper.axisAlingedPlaneOf(facingVec)).normalize().scale(0.25);
                        Vec3 offset2 = randVec.add(vec);
                        randVec = randVec.scale(0.35f);
                        this.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack),
                                offset2.x, offset2.y, offset2.z, randVec.x, randVec.y, randVec.z);
                    }
                }
            }
            int prev = this.countDown;
            this.countDown -= this.getCountDownSpeed();
            if (this.countDown < 1000 && prev >= 1000 && runLogic) {
                AllSoundEvents.CRAFTER_CLICK.playOnServer(this.level, (Vec3i) this.worldPosition, 1f, 2f);
                AllSoundEvents.CRAFTER_CRAFT.playOnServer(this.level, (Vec3i) this.worldPosition);
            }
            if (this.countDown < 0) {
                this.countDown = 0;
                if (!runLogic) return;
                this.tryInsert();
                return;
            }
        }

        if (this.phase == Phase.INSERTING) {
            if (runLogic && this.isTargetingBelt())
                this.tryInsert();
        }
    }

    protected boolean isTargetingBelt() {
        DirectBeltInputBehaviour behaviour = this.getTargetingBelt();
        return behaviour != null && behaviour.canInsertFromSide(this.getTargetDirection());
    }

    protected DirectBeltInputBehaviour getTargetingBelt() {
        BlockPos targetPos = this.worldPosition.relative(this.getTargetDirection());
        return BlockEntityBehaviour.get(this.level, targetPos, DirectBeltInputBehaviour.TYPE);
    }

    public void tryInsert() {
        if (!this.inserting.hasInventory() && !this.isTargetingBelt()) {
            this.ejectWholeGrid();
            return;
        }
        boolean changedPhase = this.phase != Phase.INSERTING;
        LinkedList<Pair<Integer, Integer>> inserted = new LinkedList<>();
        DirectBeltInputBehaviour behaviour = this.getTargetingBelt();
        for (Map.Entry<Pair<Integer, Integer>, ItemStack> entry : this.groupedItems.grid.entrySet()) {
            Pair<Integer, Integer> pair = entry.getKey();
            ItemStack stack = entry.getValue();
            BlockFace face = this.getTargetFace(this.level, this.worldPosition, this.getBlockState());
            ItemStack remainder = behaviour == null
                    ? this.inserting.insert(stack.copy())
                    : behaviour.handleInsertion(stack, face.getFace(), false);
            if (!remainder.isEmpty()) {
                stack.setCount(remainder.getCount());
                continue;
            }
            inserted.add(pair);
        }
        inserted.forEach(this.groupedItems.grid::remove);
        if (this.groupedItems.grid.isEmpty())
            this.ejectWholeGrid();
        else
            this.phase = Phase.INSERTING;
        if (!inserted.isEmpty() || changedPhase)
            this.sendData();
    }

    public void ejectWholeGrid() {
        List<BatchMechanicalCrafterBlockEntity> chain =
                BatchRecipeGridHandler.getAllCraftersOfChain(this);
        if (chain == null) return;
        chain.forEach(BatchMechanicalCrafterBlockEntity::eject);
        if (!this.level.isClientSide()) {
            for (BatchMechanicalCrafterBlockEntity crafter : chain) {
                crafter.tryProcessPackagerBox();
            }
        }
    }

    public void eject() {
        BlockState blockState = this.getBlockState();
        boolean present = BatchCrafterHelper.isBatchCrafter(blockState);
        Vec3 vec = present
                ? Vec3.atLowerCornerOf((Vec3i) blockState.getValue(BatchMechanicalCrafterBlock.HORIZONTAL_FACING)
                .getNormal()).scale(0.75)
                : Vec3.ZERO;
        Vec3 ejectPos = VecHelper.getCenterOf((Vec3i) this.worldPosition).add(vec);
        this.groupedItems.grid.forEach((pair, stack) -> this.dropItem(ejectPos, stack));
        if (!this.inventory.getItem(0).isEmpty())
            this.dropItem(ejectPos, this.inventory.getItem(0));
        this.phase = Phase.IDLE;
        this.groupedItems = new BatchRecipeGridHandler.GroupedItems();
        this.inventory.setStackInSlot(0, ItemStack.EMPTY);
        this.sendData();
    }

    private void tryProcessPackagerBoxChainWide() {
        List<BatchMechanicalCrafterBlockEntity> chain = BatchRecipeGridHandler.getAllCraftersOfChain(this);
        if (chain == null) {
            this.tryProcessPackagerBox();
            return;
        }
        chain.forEach(BatchMechanicalCrafterBlockEntity::tryProcessPackagerBox);
    }

    private void tryProcessPackagerBox() {
        var facing = this.getBlockState()
                .getOptionalValue(BatchMechanicalCrafterBlock.HORIZONTAL_FACING);
        if (facing.isEmpty()) return;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            net.minecraft.core.BlockPos neighborPos = this.worldPosition.relative(dir);
            net.minecraft.world.level.block.entity.BlockEntity neighbor =
                    this.level.getBlockEntity(neighborPos);
            if (!(neighbor instanceof com.simibubi.create.content.logistics.packager.PackagerBlockEntity packager))
                continue;
            if (packager.heldBox.isEmpty()) continue;
            if (packager.animationTicks != 0) continue;
            com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts ctx =
                    com.simibubi.create.content.logistics.box.PackageItem.getOrderContext(packager.heldBox);
            if (ctx == null || ctx.orderedCrafts().isEmpty()) continue;
            var packagerFacing = packager.getBlockState()
                    .getOptionalValue(com.simibubi.create.content.logistics.packager.PackagerBlock.FACING)
                    .orElse(null);
            if (packagerFacing == null) continue;
            net.minecraft.core.BlockPos packagerTarget =
                    packager.getBlockPos().relative(packagerFacing.getOpposite());
            if (!packagerTarget.equals(this.worldPosition)) continue;
            com.molox.createimp.block.batch_mechanical_crafter.BatchCrafterUnpackingHandler
                    .processBatchPackage(packager, this);
            break;
        }
    }

    public void dropItem(Vec3 ejectPos, ItemStack stack) {
        if (stack.isEmpty()) return;
        int remaining = stack.getCount();
        int maxStack = Math.max(1, stack.getMaxStackSize());
        while (remaining > 0) {
            int dropCount = Math.min(remaining, maxStack);
            ItemEntity itemEntity = new ItemEntity(this.level, ejectPos.x, ejectPos.y, ejectPos.z,
                    stack.copyWithCount(dropCount));
            itemEntity.setDefaultPickUpDelay();
            this.level.addFreshEntity((Entity) itemEntity);
            remaining -= dropCount;
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (this.level.isClientSide && !this.isVirtual()) return;
        if (this.phase == Phase.IDLE && this.craftingItemPresent())
            this.checkCompletedRecipe(false);
        // initialize()内部会在behaviour系统完成第一次目标扫描之前，
        // 提前同步调用一次lazyTick()（SmartBlockEntity.initialize -> this.lazyTick()，
        // 发生在super.tick()内部、早于tickedOnce被置true的时机）。
        // 这一刻inserting.hasInventory()必然是false（还没来得及扫描），
        // 如果照常调用tryInsert()会被误判为"没有可用输出目标"而把整格物品扔到地上。
        // 跳过这一次，把重试机会留给之后正常的lazyTick周期（此时行为已完成过扫描）。
        if (this.phase == Phase.INSERTING && this.tickedOnce)
            this.tryInsert();

    }

    public boolean craftingItemPresent() {
        return !this.inventory.getItem(0).isEmpty();
    }

    public boolean craftingItemOrCoverPresent() {
        return !this.inventory.getItem(0).isEmpty() || this.covered;
    }

    public void checkCompletedRecipe(boolean poweredStart) {
        if (this.getSpeed() == 0f) return;
        if (this.level.isClientSide && !this.isVirtual()) return;
        List<BatchMechanicalCrafterBlockEntity> chain = BatchRecipeGridHandler.getAllCraftersOfChainIf(
                this,
                poweredStart ? BatchMechanicalCrafterBlockEntity::craftingItemPresent
                        : BatchMechanicalCrafterBlockEntity::craftingItemOrCoverPresent,
                poweredStart);
        if (chain == null) return;
        chain.forEach(BatchMechanicalCrafterBlockEntity::begin);
    }

    protected void begin() {
        this.pendingForcedStart = false;
        this.phase = Phase.ACCEPTING;
        this.groupedItems = new BatchRecipeGridHandler.GroupedItems(this.inventory.getItem(0).copy());
        this.inventory.setStackInSlot(0, ItemStack.EMPTY);
        if (BatchRecipeGridHandler.getPrecedingCrafters(this).isEmpty()) {
            this.phase = Phase.ASSEMBLING;
            this.countDown = 1;
        }
        this.sendData();
    }

    protected void continueIfAllPrecedingFinished() {
        List<BatchMechanicalCrafterBlockEntity> preceding = BatchRecipeGridHandler.getPrecedingCrafters(this);
        if (preceding == null) {
            this.ejectWholeGrid();
            return;
        }
        for (BatchMechanicalCrafterBlockEntity blockEntity : preceding) {
            if (blockEntity.phase == Phase.WAITING) continue;
            return;
        }
        this.phase = Phase.ASSEMBLING;
        this.countDown = 1;
    }

    public void connectivityChanged() {
        this.reRender = true;
        this.sendData();
        this.invCap = null;
        this.invalidateCapabilities();
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public void setScriptedResult(ItemStack scriptedResult) {
        this.scriptedResult = scriptedResult;
    }

    public void clearPackageProgress() {
        this.packageProgressOrderId = -1;
        this.packageProgressEntryIndex = 0;
        this.packageProgressEntryRemaining = -1;
        this.setChanged();
    }

    public BatchConnectedInputHandler.ConnectedInput getInput() {
        return this.input;
    }

    public enum Phase {
        IDLE, ACCEPTING, ASSEMBLING, EXPORTING, WAITING, CRAFTING, INSERTING
    }

    public static class Inventory extends SmartInventory {
        private final BatchMechanicalCrafterBlockEntity blockEntity;

        public Inventory(BatchMechanicalCrafterBlockEntity blockEntity) {
            super(1, (com.simibubi.create.foundation.blockEntity.SyncedBlockEntity) blockEntity, 64, false);
            this.blockEntity = blockEntity;
            this.forbidExtraction();
            this.whenContentsChanged(slot -> {
                if (this.getItem(slot).isEmpty()) {
                    // 格子刚变空，且合成器处于完全空闲状态（不是begin()消耗物品进入
                    // ACCEPTING阶段的过程中）——说明这是一次"释放"，可能有打包机内
                    // 因为该格子曾经非空而被我们卡住的合成请求包裹，此时重新尝试处理
                    // 整条合成器链上的所有相邻打包机。
                    if (blockEntity.phase == Phase.IDLE
                            && blockEntity.getLevel() != null
                            && !blockEntity.getLevel().isClientSide())
                        blockEntity.tryProcessPackagerBoxChainWide();
                    return;
                }
                if (blockEntity.phase == Phase.IDLE)
                    blockEntity.checkCompletedRecipe(false);
            });
        }

        public BatchMechanicalCrafterBlockEntity getBlockEntity() {
            return this.blockEntity;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (this.blockEntity.phase != Phase.IDLE) return stack;
            if (this.blockEntity.covered) return stack;
            if (!this.getItem(slot).isEmpty()) return stack;
            ItemStack result = super.insertItem(slot, stack, simulate);
            if (result.getCount() != stack.getCount() && !simulate) {
                this.blockEntity.getLevel().playSound(null, this.blockEntity.getBlockPos(),
                        SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.25f, 0.5f);
            }
            return result;
        }
    }
}