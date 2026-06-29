package com.molox.createimp.registry;

import com.molox.createimp.block.andesite_scrap_bucket.AndesiteScrapBucketBlockEntity;
import com.molox.createimp.block.batch_mechanical_crafter.BatchMechanicalCrafterBlockEntity;
import com.molox.createimp.block.batch_repackager.BatchRepackagerBlockEntity;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class ModCapabilities {

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntityTypes.ANDESITE_SCRAP_BUCKET.get(),
                (be, direction) -> AndesiteScrapBucketBlockEntity.VOID_ITEM_HANDLER
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntityTypes.ANDESITE_SCRAP_BUCKET.get(),
                (be, direction) -> AndesiteScrapBucketBlockEntity.VOID_FLUID_HANDLER
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntityTypes.BRASS_SCRAP_BUCKET.get(),
                (be, direction) -> be.itemHandler
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntityTypes.BRASS_SCRAP_BUCKET.get(),
                (be, direction) -> be.fluidHandler
        );
        BatchMechanicalCrafterBlockEntity.registerCapabilities(
                event, ModBlockEntityTypes.BATCH_MECHANICAL_CRAFTER.get());
        BatchRepackagerBlockEntity.registerCapabilities(event);
    }
}