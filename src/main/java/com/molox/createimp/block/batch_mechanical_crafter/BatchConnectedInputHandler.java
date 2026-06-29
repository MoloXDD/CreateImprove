package com.molox.createimp.block.batch_mechanical_crafter;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedList;
import java.util.stream.Collectors;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;

public class BatchConnectedInputHandler {

    public static boolean shouldConnect(Level world, BlockPos pos, Direction face, Direction direction) {
        BlockState refState = world.getBlockState(pos);
        if (!refState.hasProperty(HorizontalKineticBlock.HORIZONTAL_FACING))
            return false;
        Direction refDirection = refState.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        if (direction.getAxis() == refDirection.getAxis())
            return false;
        if (face == refDirection)
            return false;
        BlockState neighbour = world.getBlockState(pos.relative(direction));
        if (!BatchCrafterHelper.isBatchCrafter(neighbour))
            return false;
        return refDirection == neighbour.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
    }

    public static void toggleConnection(Level world, BlockPos pos, BlockPos pos2) {
        BatchMechanicalCrafterBlockEntity crafter1 = BatchCrafterHelper.getCrafter(world, pos);
        BatchMechanicalCrafterBlockEntity crafter2 = BatchCrafterHelper.getCrafter(world, pos2);
        if (crafter1 == null || crafter2 == null)
            return;

        BlockPos controllerPos1 = crafter1.getBlockPos().offset((Vec3i) crafter1.input.data.get(0));
        BlockPos controllerPos2 = crafter2.getBlockPos().offset((Vec3i) crafter2.input.data.get(0));

        if (controllerPos1.equals(controllerPos2)) {
            BatchMechanicalCrafterBlockEntity controller = BatchCrafterHelper.getCrafter(world, controllerPos1);
            Set<BlockPos> positions = controller.input.data.stream()
                    .map(offset -> controllerPos1.offset(offset))
                    .collect(Collectors.toSet());
            LinkedList<BlockPos> frontier = new LinkedList<>();
            ArrayList<BlockPos> splitGroup = new ArrayList<>();
            frontier.add(pos2);
            positions.remove(pos2);
            positions.remove(pos);
            while (!frontier.isEmpty()) {
                BlockPos current = frontier.remove(0);
                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction);
                    if (!positions.remove(next))
                        continue;
                    splitGroup.add(next);
                    frontier.add(next);
                }
            }
            initAndAddAll(world, crafter1, positions);
            initAndAddAll(world, crafter2, splitGroup);
            crafter1.setChanged();
            crafter1.connectivityChanged();
            crafter2.setChanged();
            crafter2.connectivityChanged();
            return;
        }

        if (!crafter1.input.isController)
            crafter1 = BatchCrafterHelper.getCrafter(world, controllerPos1);
        if (!crafter2.input.isController)
            crafter2 = BatchCrafterHelper.getCrafter(world, controllerPos2);
        if (crafter1 == null || crafter2 == null)
            return;

        connectControllers(world, crafter1, crafter2);
        world.setBlock(crafter1.getBlockPos(), crafter1.getBlockState(), 3);
        crafter1.setChanged();
        crafter1.connectivityChanged();
        crafter2.setChanged();
        crafter2.connectivityChanged();
    }

    public static void initAndAddAll(Level world, BatchMechanicalCrafterBlockEntity crafter,
                                     java.util.Collection<BlockPos> positions) {
        crafter.input = new ConnectedInput();
        positions.forEach(splitPos -> modifyAndUpdate(world, splitPos, input -> {
            input.attachTo(crafter.getBlockPos(), splitPos);
            crafter.input.data.add(splitPos.subtract((Vec3i) crafter.getBlockPos()));
        }));
    }

    public static void connectControllers(Level world, BatchMechanicalCrafterBlockEntity crafter1,
                                          BatchMechanicalCrafterBlockEntity crafter2) {
        crafter1.input.data.forEach(offset -> {
            BlockPos connectedPos = crafter1.getBlockPos().offset((Vec3i) offset);
            modifyAndUpdate(world, connectedPos, input -> {
            });
        });
        crafter2.input.data.forEach(offset -> {
            if (offset.equals(BlockPos.ZERO))
                return;
            BlockPos connectedPos = crafter2.getBlockPos().offset((Vec3i) offset);
            modifyAndUpdate(world, connectedPos, input -> {
                input.attachTo(crafter1.getBlockPos(), connectedPos);
                crafter1.input.data.add(BlockPos.ZERO.subtract((Vec3i) input.data.get(0)));
            });
        });
        crafter2.input.attachTo(crafter1.getBlockPos(), crafter2.getBlockPos());
        crafter1.input.data.add(BlockPos.ZERO.subtract((Vec3i) crafter2.input.data.get(0)));
    }

    private static void modifyAndUpdate(Level world, BlockPos pos,
                                        java.util.function.Consumer<ConnectedInput> callback) {
        net.minecraft.world.level.block.entity.BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof BatchMechanicalCrafterBlockEntity))
            return;
        BatchMechanicalCrafterBlockEntity crafter = (BatchMechanicalCrafterBlockEntity) blockEntity;
        callback.accept(crafter.input);
        crafter.setChanged();
        crafter.connectivityChanged();
    }

    public static class ConnectedInput {
        boolean isController = true;
        List<BlockPos> data = Collections.synchronizedList(new ArrayList<>());

        public ConnectedInput() {
            this.data.add(BlockPos.ZERO);
        }

        public void attachTo(BlockPos controllerPos, BlockPos myPos) {
            this.isController = false;
            this.data.clear();
            this.data.add(controllerPos.subtract((Vec3i) myPos));
        }

        public IItemHandler getItemHandler(Level world, BlockPos pos) {
            List<BatchMechanicalCrafterBlockEntity.Inventory> inventories = this.getInventories(world, pos);
            return new CombinedInvWrapper(inventories.toArray(IItemHandlerModifiable[]::new));
        }

        public List<BatchMechanicalCrafterBlockEntity.Inventory> getInventories(Level world, BlockPos pos) {
            if (!this.isController) {
                BlockPos controllerPos = pos.offset((Vec3i) this.data.get(0));
                ConnectedInput input = BatchCrafterHelper.getInput(world, controllerPos);
                if (input == this || input == null || !input.isController)
                    return List.of();
                return input.getInventories(world, controllerPos);
            }
            Direction facing = Direction.SOUTH;
            BlockState blockState = world.getBlockState(pos);
            if (blockState.hasProperty(BatchMechanicalCrafterBlock.HORIZONTAL_FACING))
                facing = blockState.getValue(BatchMechanicalCrafterBlock.HORIZONTAL_FACING);
            Direction.AxisDirection axisDirection = facing.getAxisDirection();
            Direction.Axis compareAxis = facing.getClockWise().getAxis();
            Comparator<BlockPos> invOrdering = (p1, p2) -> {
                int compareY = -Integer.compare(p1.getY(), p2.getY());
                int modifier = axisDirection.getStep() * (compareAxis == Direction.Axis.Z ? -1 : 1);
                int c1 = compareAxis.choose(p1.getX(), p1.getY(), p1.getZ());
                int c2 = compareAxis.choose(p2.getX(), p2.getY(), p2.getZ());
                return compareY != 0 ? compareY : modifier * Integer.compare(c1, c2);
            };
            return this.data.stream()
                    .sorted(invOrdering)
                    .map(l -> BatchCrafterHelper.getCrafter(world, pos.offset((Vec3i) l)))
                    .filter(Objects::nonNull)
                    .map(BatchMechanicalCrafterBlockEntity::getInventory)
                    .collect(Collectors.toList());
        }

        public void write(CompoundTag nbt) {
            nbt.putBoolean("Controller", this.isController);
            ListTag list = new ListTag();
            this.data.forEach(p -> {
                CompoundTag data = new CompoundTag();
                data.putInt("X", p.getX());
                data.putInt("Y", p.getY());
                data.putInt("Z", p.getZ());
                list.add(data);
            });
            nbt.put("Data", (Tag) list);
        }

        public void read(CompoundTag nbt) {
            this.isController = nbt.getBoolean("Controller");
            this.data = NBTHelper.readCompoundList(nbt.getList("Data", 10),
                    c -> new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z")));
            if (this.data.isEmpty()) {
                this.isController = true;
                this.data.add(BlockPos.ZERO);
            }
        }
    }
}