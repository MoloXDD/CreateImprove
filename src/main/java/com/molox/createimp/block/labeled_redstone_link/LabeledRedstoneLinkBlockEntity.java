package com.molox.createimp.block.labeled_redstone_link;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class LabeledRedstoneLinkBlockEntity extends BlockEntity {

    public static final String DEFAULT_FREQUENCY = "默认红石频率";

    private String frequencyText = DEFAULT_FREQUENCY;

    public LabeledRedstoneLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public String getFrequencyText() {
        return frequencyText;
    }

    public void setFrequencyText(String text) {
        this.frequencyText = (text == null || text.isBlank()) ? DEFAULT_FREQUENCY : text;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("FrequencyText", frequencyText);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        frequencyText = tag.getString("FrequencyText");
        if (frequencyText.isBlank()) frequencyText = DEFAULT_FREQUENCY;
    }
}