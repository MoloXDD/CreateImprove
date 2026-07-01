package com.molox.createimp.block.template_panel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class TemplatePanelConnection {

    public static final Codec<TemplatePanelConnection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TemplatePanelPosition.CODEC.fieldOf("position").forGetter(i -> i.from),
            Codec.INT.fieldOf("amount").forGetter(i -> i.amount),
            Codec.INT.fieldOf("arrow_bending").forGetter(i -> i.arrowBendMode)
    ).apply(instance, TemplatePanelConnection::new));

    public TemplatePanelPosition from;
    public int amount;
    public List<Direction> path;
    public int arrowBendMode;
    public boolean success;
    public WeakReference<Object> cachedSource;
    private int arrowBendModeCurrentPathUses;

    public TemplatePanelConnection(TemplatePanelPosition from, int amount) {
        this(from, amount, -1);
    }

    public TemplatePanelConnection(TemplatePanelPosition from, int amount, int arrowBendMode) {
        this.from = from;
        this.amount = amount;
        this.arrowBendMode = arrowBendMode;
        this.path = new ArrayList<>();
        this.success = true;
        this.arrowBendModeCurrentPathUses = 0;
        this.cachedSource = new WeakReference<>(null);
    }

    public List<Direction> getPath(Level level, BlockState state, TemplatePanelPosition to) {
        if (!this.path.isEmpty() && this.arrowBendModeCurrentPathUses == this.arrowBendMode) {
            return this.path;
        }
        boolean findSuitable = this.arrowBendMode == -1;
        this.arrowBendModeCurrentPathUses = this.arrowBendMode;
        TemplatePanelBehaviour fromBehaviour = TemplatePanelBehaviour.at(level, to);
        Vec3 diff = this.calculatePathDiff(state, to);
        Vec3 start = fromBehaviour != null
                ? fromBehaviour.getSlotPositioning().getLocalOffset(level, to.pos(), state).add(Vec3.atLowerCornerOf(to.pos()))
                : Vec3.ZERO;
        float xRot = 57.295776f * TemplatePanelBlock.getXRot(state);
        float yRot = 57.295776f * TemplatePanelBlock.getYRot(state);

        block0:
        for (int actualMode = 0; actualMode <= 4; ++actualMode) {
            this.path.clear();
            if (!findSuitable && actualMode != this.arrowBendMode) continue;
            boolean desperateOption = actualMode == 4;
            BlockPos toTravelFirst = BlockPos.ZERO;
            BlockPos toTravelLast = BlockPos.containing(diff.scale(2.0).add(0.1, 0.1, 0.1));
            if (actualMode > 1) {
                boolean flipX = diff.x > 0.0 ^ actualMode % 2 == 1;
                boolean flipZ = diff.z > 0.0 ^ actualMode % 2 == 0;
                int ceilX = Mth.positiveCeilDiv(toTravelLast.getX(), 2);
                int ceilZ = Mth.positiveCeilDiv(toTravelLast.getZ(), 2);
                int floorZ = Mth.floorDiv(toTravelLast.getZ(), 2);
                int floorX = Mth.floorDiv(toTravelLast.getX(), 2);
                toTravelFirst = new BlockPos(flipX ? floorX : ceilX, 0, flipZ ? floorZ : ceilZ);
                toTravelLast = new BlockPos(!flipX ? floorX : ceilX, 0, !flipZ ? floorZ : ceilZ);
            }
            Direction lastDirection = null;
            Direction currentDirection = null;
            for (BlockPos toTravel : List.of(toTravelFirst, toTravelLast)) {
                boolean zIsFarther = Math.abs(toTravel.getZ()) > Math.abs(toTravel.getX());
                boolean zIsPreferred = desperateOption ? zIsFarther : actualMode % 2 == 1;
                List<Direction> directionOrder = zIsPreferred
                        ? List.of(Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST)
                        : List.of(Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH);
                for (int i = 0; i < 100 && !toTravel.equals(BlockPos.ZERO); ++i) {
                    for (Direction d : directionOrder) {
                        if (lastDirection != null && d == lastDirection.getOpposite()) continue;
                        if (currentDirection != null
                                && toTravel.relative(d).distManhattan(BlockPos.ZERO) >= toTravel.relative(currentDirection).distManhattan(BlockPos.ZERO)) continue;
                        currentDirection = d;
                    }
                    lastDirection = currentDirection;
                    toTravel = toTravel.relative(currentDirection);
                    this.path.add(currentDirection);
                }
            }
            if (!findSuitable || desperateOption) break;
            BlockPos travelled = BlockPos.ZERO;
            for (int i = 0; i < this.path.size() - 1; ++i) {
                Direction d = this.path.get(i);
                travelled = travelled.relative(d);
                Vec3 testOffset = Vec3.atLowerCornerOf(travelled).scale(0.5);
                testOffset = VecHelper.rotate(testOffset, 180.0, Direction.Axis.Y);
                testOffset = VecHelper.rotate(testOffset, xRot + 90.0f, Direction.Axis.X);
                Vec3 v = start.add(testOffset = VecHelper.rotate(testOffset, yRot, Direction.Axis.Y));
                if (!level.noCollision(new AABB(v, v).inflate(0.0078125))) continue block0;
            }
        }
        return this.path;
    }

    public Vec3 calculatePathDiff(BlockState state, TemplatePanelPosition to) {
        float xRot = 57.295776f * TemplatePanelBlock.getXRot(state);
        float yRot = 57.295776f * TemplatePanelBlock.getYRot(state);
        int slotDiffx = to.slot().xOffset - this.from.slot().xOffset;
        int slotDiffY = to.slot().yOffset - this.from.slot().yOffset;
        Vec3 diff = Vec3.atLowerCornerOf(to.pos().subtract((Vec3i) this.from.pos()));
        diff = VecHelper.rotate(diff, -yRot, Direction.Axis.Y);
        diff = VecHelper.rotate(diff, -xRot - 90.0f, Direction.Axis.X);
        diff = VecHelper.rotate(diff, -180.0, Direction.Axis.Y);
        diff = diff.add(slotDiffx * 0.5, 0.0, slotDiffY * 0.5);
        diff = diff.multiply(1.0, 0.0, 1.0);
        return diff;
    }
}