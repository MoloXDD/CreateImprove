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

    public static final DeferredItem<BlockItem> BATCH_REPACKAGER =
            ITEMS.registerSimpleBlockItem("batch_repackager", ModBlocks.BATCH_REPACKAGER,
                    new Item.Properties());

    public static final DeferredItem<com.molox.createimp.block.template_panel.TemplatePanelBlockItem> TEMPLATE_PANEL =
            ITEMS.register("template_panel",
                    () -> new com.molox.createimp.block.template_panel.TemplatePanelBlockItem(
                            ModBlocks.TEMPLATE_PANEL.get(), new Item.Properties()));

    public static final DeferredItem<com.molox.createimp.block.work_warehouse.WorkWarehouseBlockItem> WORK_WAREHOUSE =
            ITEMS.register("work_warehouse",
                    () -> new com.molox.createimp.block.work_warehouse.WorkWarehouseBlockItem(
                            ModBlocks.WORK_WAREHOUSE.get(), new Item.Properties()));

    public static final DeferredItem<com.molox.createimp.block.process_manager.ProcessManagerBlockItem> PROCESS_MANAGER =
            ITEMS.register("process_manager",
                    () -> new com.molox.createimp.block.process_manager.ProcessManagerBlockItem(
                            ModBlocks.PROCESS_MANAGER.get(), new Item.Properties()));

    public static final DeferredItem<com.molox.createimp.item.TemplateFluidTokenItem> TEMPLATE_FLUID_TOKEN =
            ITEMS.register("template_fluid_token",
                    () -> new com.molox.createimp.item.TemplateFluidTokenItem(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<BlockItem> REDSTONE_LINK_ROUTER =
            ITEMS.registerSimpleBlockItem("redstone_link_router", ModBlocks.REDSTONE_LINK_ROUTER,
                    new Item.Properties());
}