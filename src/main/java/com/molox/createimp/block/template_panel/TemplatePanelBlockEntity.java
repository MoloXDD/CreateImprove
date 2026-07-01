package com.molox.createimp.block.template_panel;

import com.molox.createimp.registry.ModItems;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

public class TemplatePanelBlockEntity extends SmartBlockEntity {

    public EnumMap<TemplatePanelBlock.PanelSlot, TemplatePanelBehaviour> panels;
    public boolean redraw;
    public VoxelShape lastShape;

    public TemplatePanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(this.worldPosition).inflate(8.0);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.panels = new EnumMap<>(TemplatePanelBlock.PanelSlot.class);
        this.redraw = true;
        for (TemplatePanelBlock.PanelSlot slot : TemplatePanelBlock.PanelSlot.values()) {
            TemplatePanelBehaviour e = new TemplatePanelBehaviour(this, slot);
            this.panels.put(slot, e);
            behaviours.add(e);
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (this.level.isClientSide()) {
            return;
        }
        this.pruneStaleConnections();
        if (this.activePanels() == 0) {
            this.level.setBlockAndUpdate(this.worldPosition, Blocks.AIR.defaultBlockState());
        }
    }

    private void pruneStaleConnections() {
        for (TemplatePanelBehaviour behaviour : this.panels.values()) {
            if (!behaviour.isActive() || behaviour.targetedBy.isEmpty()) continue;
            boolean changed = false;
            java.util.Iterator<java.util.Map.Entry<TemplatePanelPosition, TemplatePanelConnection>> iterator =
                    behaviour.targetedBy.entrySet().iterator();
            while (iterator.hasNext()) {
                TemplatePanelPosition from = iterator.next().getKey();
                if (TemplatePanelBehaviour.getExternalFilter(this.level, from.pos(), from.slot()).isEmpty()) {
                    iterator.remove();
                    changed = true;
                }
            }
            if (changed) {
                this.redraw = true;
                this.notifyUpdate();
            }
        }
    }

    public int activePanels() {
        int result = 0;
        for (TemplatePanelBehaviour panelBehaviour : this.panels.values()) {
            if (!panelBehaviour.isActive()) continue;
            ++result;
        }
        return result;
    }

    @Override
    public void remove() {
        for (TemplatePanelBehaviour panelBehaviour : this.panels.values()) {
            if (!panelBehaviour.isActive()) continue;
            panelBehaviour.disconnectAll();
        }
        super.remove();
    }

    @Override
    public void destroy() {
        super.destroy();
        int panelCount = this.activePanels();
        if (panelCount > 1) {
            Block.popResource(this.level, this.worldPosition, new ItemStack(ModItems.TEMPLATE_PANEL.get(), panelCount - 1));
        }
    }

    public boolean addPanel(TemplatePanelBlock.PanelSlot slot, UUID frequency) {
        TemplatePanelBehaviour behaviour = this.panels.get(slot);
        if (behaviour != null && !behaviour.isActive()) {
            behaviour.enable();
            if (frequency != null) {
                behaviour.setNetwork(frequency);
            }
            this.redraw = true;
            this.lastShape = null;
            if (this.activePanels() > 1) {
                SoundType soundType = this.getBlockState().getSoundType();
                this.level.playSound(null, this.worldPosition, soundType.getPlaceSound(), SoundSource.BLOCKS,
                        (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f);
            }
            return true;
        }
        return false;
    }

    public boolean removePanel(TemplatePanelBlock.PanelSlot slot) {
        TemplatePanelBehaviour behaviour = this.panels.get(slot);
        if (behaviour != null && behaviour.isActive()) {
            behaviour.disable();
            this.redraw = true;
            this.lastShape = null;
            if (this.activePanels() > 0) {
                SoundType soundType = this.getBlockState().getSoundType();
                this.level.playSound(null, this.worldPosition, soundType.getBreakSound(), SoundSource.BLOCKS,
                        (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f);
            }
            return true;
        }
        return false;
    }

    public VoxelShape getShape() {
        if (this.lastShape != null) {
            return this.lastShape;
        }
        float xRot = 57.295776f * TemplatePanelBlock.getXRot(this.getBlockState()) + 90.0f;
        float yRot = 57.295776f * TemplatePanelBlock.getYRot(this.getBlockState());
        Direction connectedDirection = TemplatePanelBlock.connectedDirection(this.getBlockState());
        Vec3 inflateAxes = VecHelper.axisAlingedPlaneOf(connectedDirection);
        this.lastShape = Shapes.empty();
        for (TemplatePanelBehaviour behaviour : this.panels.values()) {
            if (!behaviour.isActive()) continue;
            TemplatePanelPosition panelPosition = behaviour.getPanelPosition();
            Vec3 vec = new Vec3(0.25 + panelPosition.slot().xOffset * 0.5, 0.0625, 0.25 + panelPosition.slot().yOffset * 0.5);
            vec = VecHelper.rotateCentered(vec, 180.0, Direction.Axis.Y);
            vec = VecHelper.rotateCentered(vec, xRot, Direction.Axis.X);
            vec = VecHelper.rotateCentered(vec, yRot, Direction.Axis.Y);
            AABB bb = new AABB(vec, vec).inflate(0.0625)
                    .inflate(inflateAxes.x * 3.0 / 16.0, inflateAxes.y * 3.0 / 16.0, inflateAxes.z * 3.0 / 16.0);
            this.lastShape = Shapes.or(this.lastShape, Shapes.create(bb));
        }
        return this.lastShape;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket && tag.contains("Redraw")) {
            this.lastShape = null;
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 16);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket && this.redraw) {
            NBTHelper.putMarker(tag, "Redraw");
            this.redraw = false;
        }
    }
}