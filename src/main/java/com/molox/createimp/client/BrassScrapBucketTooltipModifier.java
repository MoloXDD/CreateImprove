package com.molox.createimp.client;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketBlockEntity;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class BrassScrapBucketTooltipModifier implements TooltipModifier {

    private static final String KEY = "block.createimp.brass_scrap_bucket.tooltip";

    private final FontHelper.Palette palette;

    public BrassScrapBucketTooltipModifier(FontHelper.Palette palette) {
        this.palette = palette;
    }

    @Override
    public void modify(ItemTooltipEvent context) {
        ItemDescription description = build();
        if (description == null) return;
        context.getToolTip().addAll(1, description.getCurrentLines());
    }

    private ItemDescription build() {
        if (!I18n.exists(KEY + ".summary")) return null;

        Item produceItem = BrassScrapBucketBlockEntity.resolveProduceItem(CreateImp.getConfig());
        boolean produces = produceItem != null;
        String itemName = produces ? new ItemStack(produceItem).getHoverName().getString() : null;

        ItemDescription.Builder builder = new ItemDescription.Builder(palette);

        builder.addSummary(produces
                ? I18n.get(KEY + ".summary", itemName)
                : I18n.get(KEY + ".summary_disabled"));

        if (produces) {
            int maxStack = produceItem.getDefaultInstance().getMaxStackSize();
            builder.addBehaviour(
                    I18n.get(KEY + ".condition1"),
                    I18n.get(KEY + ".behaviour1", itemName, maxStack));
        }

        if (I18n.exists(KEY + ".condition2")) {
            builder.addBehaviour(I18n.get(KEY + ".condition2"), I18n.get(KEY + ".behaviour2"));
        }

        if (I18n.exists(KEY + ".condition3")) {
            builder.addBehaviour(
                    I18n.get(KEY + ".condition3"),
                    produces
                            ? I18n.get(KEY + ".behaviour3", itemName)
                            : I18n.get(KEY + ".behaviour3_disabled"));
        }

        for (int i = 1; i < 100; i++) {
            String controlKey = KEY + ".control" + i;
            String actionKey = KEY + ".action" + i;
            if (!I18n.exists(controlKey)) break;
            builder.addAction(I18n.get(controlKey), I18n.get(actionKey));
        }

        return builder.build();
    }
}