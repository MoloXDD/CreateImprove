package com.molox.createimp.mixin;

import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StockKeeperRequestScreen.CategoryEntry.class, remap = false)
public interface StockKeeperCategoryEntryAccessor {

    @Accessor("y")
    int createimp$getY();

    @Accessor("y")
    void createimp$setY(int y);

    @Accessor("hidden")
    boolean createimp$isHidden();

    @Accessor("hidden")
    void createimp$setHidden(boolean hidden);

    @Accessor("targetBECategory")
    int createimp$getTargetBECategory();
}