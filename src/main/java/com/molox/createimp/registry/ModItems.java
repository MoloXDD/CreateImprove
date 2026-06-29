package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.NetworkManagerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreateImp.MODID);

    public static final DeferredItem<BlockItem> ANDESITE_SCRAP_BUCKET =
            ITEMS.registerSimpleBlockItem("andesite_scrap_bucket", ModBlocks.ANDESITE_SCRAP_BUCKET,
                    new Item.Properties());

    public static final DeferredItem<BlockItem> BRASS_SCRAP_BUCKET =
            ITEMS.registerSimpleBlockItem("brass_scrap_bucket", ModBlocks.BRASS_SCRAP_BUCKET,
                    new Item.Properties());

    public static final DeferredItem<NetworkManagerItem> NETWORK_MANAGER =
            ITEMS.register("network_manager",
                    () -> new NetworkManagerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> LABELED_REDSTONE_LINK =
            ITEMS.registerSimpleBlockItem("labeled_redstone_link", ModBlocks.LABELED_REDSTONE_LINK,
                    new Item.Properties());

    public static final DeferredItem<BlockItem> BATCH_MECHANICAL_CRAFTER =
            ITEMS.registerSimpleBlockItem("batch_mechanical_crafter", ModBlocks.BATCH_MECHANICAL_CRAFTER,
                    new Item.Properties());
}