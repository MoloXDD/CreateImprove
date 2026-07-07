package com.molox.createimp.block.work_warehouse;

import com.molox.createimp.network.WorkWarehouseActivateEffectPacket;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class WorkWarehouseBlockEntity extends SmartBlockEntity {

    public LogisticallyLinkedBehaviour behaviour;
    public final WorkWarehouseItemStackHandler storage = new WorkWarehouseItemStackHandler(this);
    private String address = "";
    private String targetAddress = "";

    public WorkWarehouseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.behaviour = new LogisticallyLinkedBehaviour(this, false);
        behaviours.add(this.behaviour);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
        setChanged();
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
        setChanged();
    }

    public boolean isWorking() {
        return getBlockState().getValue(WorkWarehouseBlock.POWERED);
    }

    public void setWorking(boolean working) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (isWorking() == working) {
            return;
        }
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(WorkWarehouseBlock.POWERED, working));
    }

    public void activate(String targetAddress) {
        if (level == null || level.isClientSide()) {
            return;
        }
        setTargetAddress(targetAddress);
        setWorking(true);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 center = Vec3.atCenterOf(worldPosition);
            PacketDistributor.sendToPlayersNear(serverLevel, null, center.x, center.y, center.z, 32.0,
                    new WorkWarehouseActivateEffectPacket(worldPosition));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        address = tag.getString("Address");
        targetAddress = tag.getString("TargetAddress");
        if (!clientPacket && tag.contains("Storage", Tag.TAG_COMPOUND)) {
            storage.deserializeNBT(registries, tag.getCompound("Storage"));
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Address", address);
        tag.putString("TargetAddress", targetAddress);
        if (!clientPacket) {
            tag.put("Storage", storage.serializeNBT(registries));
        }
    }
}