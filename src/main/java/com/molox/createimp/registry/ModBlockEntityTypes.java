package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.andesite_scrap_bucket.AndesiteScrapBucketBlockEntity;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketBlockEntity;
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
}