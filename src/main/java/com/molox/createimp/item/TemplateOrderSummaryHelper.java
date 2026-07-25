package com.molox.createimp.item;

import com.molox.createimp.CreateImp;
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
            try {
                summary.add(new BigItemStack(TemplateOrderTokenHelper.of(target), 1000000000));
            } catch (Exception e) {
                // 单个模板令牌生成失败时只跳过这一个、记日志，不能让这里的异常
                // 拖死整个仓管库存请求——这个方法是在仓储发报机的网络包处理里
                // 被同步调用的，这里一旦抛出去不接住，客户端会一直卡在
                // "检查库存中……"等不到回复。
                CreateImp.LOGGER.error("生成模板令牌失败，已跳过这一个模板：{}", target, e);
            }
        }
        return summary;
    }
}