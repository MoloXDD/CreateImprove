package com.molox.createimp.item;

import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.List;

public class NetworkManagerItem extends Item {

    public NetworkManagerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            List<NetworkLabel> labels = stack.getOrDefault(
                    ModDataComponents.NETWORK_MANAGER_LABELS.get(), Collections.emptyList());
            PacketDistributor.sendToPlayer(
                    (ServerPlayer) player,
                    new OpenNetworkManagerGuiPacket(hand, labels)
            );
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}