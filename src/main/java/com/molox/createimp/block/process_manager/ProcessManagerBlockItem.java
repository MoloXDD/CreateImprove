package com.molox.createimp.block.process_manager;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class ProcessManagerBlockItem extends LogisticallyLinkedBlockItem {

    public ProcessManagerBlockItem(Block block, Item.Properties properties) {
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
}