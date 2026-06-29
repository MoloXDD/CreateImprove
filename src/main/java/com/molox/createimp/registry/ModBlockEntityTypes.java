package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.andesite_scrap_bucket.AndesiteScrapBucketBlockEntity;
import com.molox.createimp.block.batch_mechanical_crafter.BatchMechanicalCrafterBlockEntity;
import com.molox.createimp.block.batch_repackager.BatchRepackagerBlockEntity;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketBlockEntity;
import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntityTypes {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, CreateImp.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AndesiteScrapBucketBlockEntity>> ANDESITE_SCRAP_BUCKET =
            BLOCK_ENTITY_TYPES.register("andesite_scrap_bucket",
                    () -> BlockEntityType.Builder.of(AndesiteScrapBucketBlockEntity::new,
                            ModBlocks.ANDESITE_SCRAP_BUCKET.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrassScrapBucketBlockEntity>> BRASS_SCRAP_BUCKET =
            BLOCK_ENTITY_TYPES.register("brass_scrap_bucket",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new BrassScrapBucketBlockEntity(
                                    ModBlockEntityTypes.BRASS_SCRAP_BUCKET.get(), pos, state),
                            ModBlocks.BRASS_SCRAP_BUCKET.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabeledRedstoneLinkBlockEntity>> LABELED_REDSTONE_LINK =
            BLOCK_ENTITY_TYPES.register("labeled_redstone_link",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new LabeledRedstoneLinkBlockEntity(
                                    ModBlockEntityTypes.LABELED_REDSTONE_LINK.get(), pos, state),
                            ModBlocks.LABELED_REDSTONE_LINK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatchMechanicalCrafterBlockEntity>> BATCH_MECHANICAL_CRAFTER =
            BLOCK_ENTITY_TYPES.register("batch_mechanical_crafter",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new BatchMechanicalCrafterBlockEntity(
                                    ModBlockEntityTypes.BATCH_MECHANICAL_CRAFTER.get(), pos, state),
                            ModBlocks.BATCH_MECHANICAL_CRAFTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatchRepackagerBlockEntity>> BATCH_REPACKAGER =
            BLOCK_ENTITY_TYPES.register("batch_repackager",
                    () -> BlockEntityType.Builder.of(
                            (pos, state) -> new BatchRepackagerBlockEntity(
                                    ModBlockEntityTypes.BATCH_REPACKAGER.get(), pos, state),
                            ModBlocks.BATCH_REPACKAGER.get()).build(null));
}