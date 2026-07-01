package com.molox.createimp.block.template_panel;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class TemplatePanelBlockItem extends LogisticallyLinkedBlockItem {

    public TemplatePanelBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        if (!isTuned(stack)) {
            AllSoundEvents.DENY.playOnServer(context.getLevel(), (Vec3i) context.getClickedPos());
            context.getPlayer().displayClientMessage(
                    CreateLang.translate("factory_panel.tune_before_placing").component(), true);
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        return super.updateCustomBlockEntityTag(pos, level, player, fixCtrlCopiedStack(stack), state);
    }

    public static ItemStack fixCtrlCopiedStack(ItemStack stack) {
        if (isTuned(stack) && networkFromStack(stack) == null) {
            CompoundTag bet = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
            UUID frequency = UUID.randomUUID();
            for (TemplatePanelBlock.PanelSlot slot : TemplatePanelBlock.PanelSlot.values()) {
                CompoundTag panelTag = bet.getCompound(CreateLang.asId(slot.name()));
                if (!panelTag.hasUUID("Freq")) continue;
                frequency = panelTag.getUUID("Freq");
            }
            bet = new CompoundTag();
            bet.putUUID("Freq", frequency);
            BlockEntity.addEntityType(bet, ((IBE<?>) ((BlockItem) stack.getItem()).getBlock()).getBlockEntityType());
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(bet));
        }
        return stack;
    }
}