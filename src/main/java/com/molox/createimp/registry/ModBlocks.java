package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.andesite_scrap_bucket.AndesiteScrapBucketBlock;
import com.molox.createimp.block.batch_mechanical_crafter.BatchMechanicalCrafterBlock;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketBlock;
import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkBlock;
import com.simibubi.create.AllBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreateImp.MODID);

    public static final DeferredBlock<AndesiteScrapBucketBlock> ANDESITE_SCRAP_BUCKET =
            BLOCKS.register("andesite_scrap_bucket", () -> new AndesiteScrapBucketBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredBlock<BrassScrapBucketBlock> BRASS_SCRAP_BUCKET =
            BLOCKS.register("brass_scrap_bucket", () -> new BrassScrapBucketBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredBlock<LabeledRedstoneLinkBlock> LABELED_REDSTONE_LINK =
            BLOCKS.register("labeled_redstone_link", () -> new LabeledRedstoneLinkBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredBlock<BatchMechanicalCrafterBlock> BATCH_MECHANICAL_CRAFTER =
            BLOCKS.register("batch_mechanical_crafter", () -> new BatchMechanicalCrafterBlock(
                    BlockBehaviour.Properties.ofFullCopy(AllBlocks.MECHANICAL_CRAFTER.get())
            ));
}