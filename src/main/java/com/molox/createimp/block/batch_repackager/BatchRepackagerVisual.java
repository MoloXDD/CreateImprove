package com.molox.createimp.block.batch_repackager;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerRenderer;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class BatchRepackagerVisual extends AbstractBlockEntityVisual<BatchRepackagerBlockEntity>
        implements SimpleDynamicVisual {

    public final TransformedInstance hatch;
    public final TransformedInstance tray;
    public float lastTrayOffset = Float.NaN;
    public PartialModel lastHatchPartial;

    public BatchRepackagerVisual(VisualizationContext ctx, BatchRepackagerBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        this.lastHatchPartial = PackagerRenderer.getHatchModel(blockEntity);
        this.hatch = this.instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(this.lastHatchPartial))
                .createInstance();
        this.tray = this.instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.PACKAGER_TRAY_DEFRAG))
                .createInstance();
        Direction facing = ((Direction) this.blockState.getValue((Property) PackagerBlock.FACING)).getOpposite();
        Vec3 lowerCorner = Vec3.atLowerCornerOf((Vec3i) facing.getNormal());
        ((TransformedInstance) ((TransformedInstance) ((TransformedInstance) ((TransformedInstance) this.hatch
                .setIdentityTransform()
                .translate((Vec3i) this.getVisualPosition()))
                .translate(lowerCorner.scale(0.49999f)))
                .rotateYCenteredDegrees(AngleHelper.horizontalAngle(facing)))
                .rotateXCenteredDegrees(AngleHelper.verticalAngle(facing)))
                .setChanged();
        this.animate(partialTick);
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        this.animate(ctx.partialTick());
    }

    public void animate(float partialTick) {
        PartialModel hatchPartial = PackagerRenderer.getHatchModel(this.blockEntity);
        if (hatchPartial != this.lastHatchPartial) {
            this.instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, Models.partial(hatchPartial))
                    .stealInstance(this.hatch);
            this.lastHatchPartial = hatchPartial;
        }
        float trayOffset = this.blockEntity.getTrayOffset(partialTick);
        if (trayOffset != this.lastTrayOffset) {
            Direction facing = ((Direction) this.blockState.getValue((Property) PackagerBlock.FACING)).getOpposite();
            Vec3 lowerCorner = Vec3.atLowerCornerOf((Vec3i) facing.getNormal());
            ((TransformedInstance) ((TransformedInstance) ((TransformedInstance) this.tray
                    .setIdentityTransform()
                    .translate((Vec3i) this.getVisualPosition()))
                    .translate(lowerCorner.scale(trayOffset)))
                    .rotateYCenteredDegrees(facing.toYRot()))
                    .setChanged();
            this.lastTrayOffset = trayOffset;
        }
    }

    @Override
    public void updateLight(float partialTick) {
        this.relight(new FlatLit[]{this.hatch, this.tray});
    }

    @Override
    protected void _delete() {
        this.hatch.delete();
        this.tray.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
    }
}