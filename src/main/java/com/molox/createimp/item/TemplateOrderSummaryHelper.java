package com.molox.createimp.item;

import com.molox.createimp.block.template_panel.TemplatePanelBehaviour;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;

import java.util.List;
import java.util.UUID;

public final class TemplateOrderSummaryHelper {

    private TemplateOrderSummaryHelper() {
    }

    public static InventorySummary augment(InventorySummary original, UUID network) {
        List<TemplateOrderTarget> targets = TemplatePanelBehaviour.collectOrderableTemplates(network);
        if (targets.isEmpty()) {
            return original;
        }
        InventorySummary summary = original.copy();
        for (TemplateOrderTarget target : targets) {
            summary.add(new BigItemStack(TemplateOrderTokenHelper.of(target), 1000000000));
        }
        return summary;
    }
}