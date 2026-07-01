package com.molox.createimp.block.template_panel;

import com.molox.createimp.CreateImp;
import com.molox.createimp.network.TemplatePanelConnectionPacket;
import com.molox.createimp.registry.ModItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

public class TemplatePanelConnectionHandler {

    static TemplatePanelPosition connectingFrom;
    static AABB connectingFromBox;
    static boolean relocating;
    static TemplatePanelPosition validRelocationTarget;

    public static boolean panelClicked(LevelAccessor level, Player player, TemplatePanelBehaviour panel) {
        if (connectingFrom == null) {
            return false;
        }
        TemplatePanelBehaviour at = TemplatePanelBehaviour.at((BlockAndTintGetter) level, connectingFrom);
        if (panel.getPanelPosition().equals(connectingFrom) || at == null) {
            player.displayClientMessage(Component.empty(), true);
            connectingFrom = null;
            connectingFromBox = null;
            return true;
        }
        String issue = checkForIssues(at, panel);
        if (issue != null) {
            player.displayClientMessage(CreateLang.translate(issue).style(ChatFormatting.RED).component(), true);
            connectingFrom = null;
            connectingFromBox = null;
            AllSoundEvents.DENY.playAt(player.level(), (Vec3i) player.blockPosition(), 1.0f, 1.0f, false);
            return true;
        }
        ItemStack filterFrom = panel.getFilter();
        ItemStack filterTo = at.getFilter();
        CatnipServices.NETWORK.sendToServer((CustomPacketPayload) new TemplatePanelConnectionPacket(panel.getPanelPosition(), connectingFrom, false));
        player.displayClientMessage(CreateLang.translate("factory_panel.panels_connected",
                filterFrom.getHoverName().getString(), filterTo.getHoverName().getString()).style(ChatFormatting.GREEN).component(), true);
        connectingFrom = null;
        connectingFromBox = null;
        player.level().playLocalSound(player.blockPosition(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 0.5f, false);
        return true;
    }

    @Nullable
    private static String checkForIssues(TemplatePanelBehaviour from, TemplatePanelBehaviour to) {
        if (from == null) {
            return "factory_panel.connection_aborted";
        }
        if (from.targetedBy.containsKey(to.getPanelPosition())) {
            return "factory_panel.already_connected";
        }
        if (from.targetedBy.size() >= 9) {
            return "factory_panel.cannot_add_more_inputs";
        }
        BlockState state1 = to.blockEntity.getBlockState();
        BlockState state2 = from.blockEntity.getBlockState();
        BlockPos diff = to.getPos().subtract((Vec3i) from.getPos());
        if (state1.getValue(TemplatePanelBlock.FACING) != state2.getValue(TemplatePanelBlock.FACING)
                || state1.getValue(TemplatePanelBlock.FACE) != state2.getValue(TemplatePanelBlock.FACE)) {
            return "factory_panel.same_orientation";
        }
        if (TemplatePanelBlock.connectedDirection(state1).getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) != 0) {
            return "factory_panel.same_surface";
        }
        if (!diff.closerThan((Vec3i) BlockPos.ZERO, 16.0)) {
            return "factory_panel.too_far_apart";
        }
        if (to.getFilter().isEmpty() || from.getFilter().isEmpty()) {
            return "factory_panel.no_item";
        }
        return null;
    }

    @Nullable
    private static String checkForVanillaTargetIssues(TemplatePanelBehaviour from, FactoryPanelBlockEntity toBE, FactoryPanelBlock.PanelSlot toSlot) {
        if (from == null) {
            return "factory_panel.connection_aborted";
        }
        if (from.targetedBy.size() >= 9) {
            return "factory_panel.cannot_add_more_inputs";
        }
        FactoryPanelBehaviour to = toBE.panels.get(toSlot);
        if (to == null || !to.isActive()) {
            return "factory_panel.connection_aborted";
        }
        BlockState state1 = toBE.getBlockState();
        BlockState state2 = from.blockEntity.getBlockState();
        BlockPos diff = toBE.getBlockPos().subtract((Vec3i) from.getPos());
        if (state1.getValue(FactoryPanelBlock.FACING) != state2.getValue(TemplatePanelBlock.FACING)
                || state1.getValue(FactoryPanelBlock.FACE) != state2.getValue(TemplatePanelBlock.FACE)) {
            return "factory_panel.same_orientation";
        }
        if (FactoryPanelBlock.connectedDirection(state1).getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) != 0) {
            return "factory_panel.same_surface";
        }
        if (!diff.closerThan((Vec3i) BlockPos.ZERO, 16.0)) {
            return "factory_panel.too_far_apart";
        }
        if (to.getFilter().isEmpty() || from.getFilter().isEmpty()) {
            return "factory_panel.no_item";
        }
        return null;
    }

    public static void clientTick() {
        if (connectingFrom == null || connectingFromBox == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        TemplatePanelBehaviour at = TemplatePanelBehaviour.at((BlockAndTintGetter) mc.level, connectingFrom);
        if (!connectingFrom.pos().closerThan((Vec3i) mc.player.blockPosition(), 16.0) || at == null) {
            connectingFrom = null;
            connectingFromBox = null;
            mc.player.displayClientMessage(Component.empty(), true);
            return;
        }
        Outliner.getInstance().showAABB(connectingFrom, connectingFromBox)
                .colored(AnimationTickHolder.getTicks() % 16 > 8 ? 0x38B8A4 : 0xA7EFF0)
                .lineWidth(0.0625f);
        mc.player.displayClientMessage(CreateLang.translate(relocating ? "factory_panel.click_to_relocate" : "factory_panel.click_second_panel").component(), true);
        if (!relocating) {
            return;
        }
        validRelocationTarget = null;
        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) {
            return;
        }
        Vec3 offsetPos = bhr.getLocation().add(Vec3.atLowerCornerOf(bhr.getDirection().getNormal()).scale(0.03125));
        BlockPos pos = BlockPos.containing((Position) offsetPos);
        BlockState blockState = at.blockEntity.getBlockState();
        TemplatePanelBlock.PanelSlot slot = TemplatePanelBlock.getTargetedSlot(pos, blockState, offsetPos);
        BlockPos diff = pos.subtract((Vec3i) connectingFrom.pos());
        Direction facing = TemplatePanelBlock.connectedDirection(blockState);
        if (facing.getAxis().choose(diff.getX(), diff.getY(), diff.getZ()) != 0) {
            return;
        }
        if (!(mc.level.getBlockState(pos).canBeReplaced() || blockState.getBlock().defaultBlockState() == mc.level.getBlockState(pos))) {
            if (!canSurvive(blockState, mc.level, pos)) {
                return;
            }
        }
        validRelocationTarget = new TemplatePanelPosition(pos, slot);
        Outliner.getInstance().showAABB("target", getBB(blockState, validRelocationTarget))
                .colored(0xEEEEEE).disableLineNormals().lineWidth(0.0625f);
    }

    private static boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return TemplatePanelBlock.canAttachLenient(level, pos, TemplatePanelBlock.connectedDirection(state).getOpposite());
    }

    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide() != net.neoforged.fml.LogicalSide.CLIENT) {
            return;
        }
        if (connectingFrom == null || connectingFromBox == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean missed = false;
        if (relocating) {
            if (mc.player.isShiftKeyDown()) {
                validRelocationTarget = null;
            }
            if (validRelocationTarget != null) {
                CatnipServices.NETWORK.sendToServer((CustomPacketPayload) new TemplatePanelConnectionPacket(validRelocationTarget, connectingFrom, true));
            }
            connectingFrom = null;
            connectingFromBox = null;
            if (validRelocationTarget == null) {
                mc.player.displayClientMessage(CreateLang.translate("factory_panel.relocation_aborted").component(), true);
            }
            relocating = false;
            validRelocationTarget = null;
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            return;
        }
        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS) {
            BlockEntity blockEntity = mc.level.getBlockEntity(bhr.getBlockPos());
            if (blockEntity instanceof FactoryPanelBlockEntity fpbe) {
                TemplatePanelBehaviour at = TemplatePanelBehaviour.at((BlockAndTintGetter) mc.level, connectingFrom);
                Vec3 hitLocation = bhr.getLocation();
                FactoryPanelBlock.PanelSlot targetSlot = FactoryPanelBlock.getTargetedSlot(bhr.getBlockPos(), fpbe.getBlockState(), hitLocation);
                String issue = checkForVanillaTargetIssues(at, fpbe, targetSlot);
                if (issue != null) {
                    mc.player.displayClientMessage(CreateLang.translate(issue).style(ChatFormatting.RED).component(), true);
                    connectingFrom = null;
                    connectingFromBox = null;
                    AllSoundEvents.DENY.playAt(mc.level, (Vec3i) mc.player.blockPosition(), 1.0f, 1.0f, false);
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    return;
                }
                TemplatePanelPosition fromPos = new TemplatePanelPosition(bhr.getBlockPos(), TemplatePanelBlock.PanelSlot.valueOf(targetSlot.name()));
                CatnipServices.NETWORK.sendToServer((CustomPacketPayload) new TemplatePanelConnectionPacket(fromPos, connectingFrom, false));
                mc.player.displayClientMessage(CreateLang.translate("factory_panel.link_connected", fpbe.getBlockState().getBlock().getName()).style(ChatFormatting.GREEN).component(), true);
                connectingFrom = null;
                connectingFromBox = null;
                mc.player.level().playLocalSound(mc.player.blockPosition(), SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.5f, 0.5f, false);
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                return;
            }
            if (!(blockEntity instanceof TemplatePanelBlockEntity)) {
                missed = true;
            }
        }
        if (!mc.player.isShiftKeyDown() && !missed) {
            return;
        }
        connectingFrom = null;
        connectingFromBox = null;
        mc.player.displayClientMessage(CreateLang.translate("factory_panel.connection_aborted").component(), true);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    public static void startRelocating(TemplatePanelBehaviour behaviour) {
        startConnection(behaviour);
        relocating = true;
    }

    public static void startConnection(TemplatePanelBehaviour behaviour) {
        relocating = false;
        connectingFrom = behaviour.getPanelPosition();
        connectingFromBox = getBB(behaviour.blockEntity.getBlockState(), connectingFrom);
    }

    public static AABB getBB(BlockState blockState, TemplatePanelPosition position) {
        Vec3 location = TemplatePanelSlotPositioning.getCenterOfSlot(blockState, position.slot()).add(Vec3.atLowerCornerOf(position.pos()));
        Vec3 plane = VecHelper.axisAlingedPlaneOf(TemplatePanelBlock.connectedDirection(blockState));
        return new AABB(location, location).inflate(plane.x * 3.0 / 16.0, plane.y * 3.0 / 16.0, plane.z * 3.0 / 16.0);
    }
}