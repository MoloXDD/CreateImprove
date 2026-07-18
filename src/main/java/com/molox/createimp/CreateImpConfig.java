package com.molox.createimp;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

@Config(name = "createimp")
public class CreateImpConfig implements ConfigData {

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public ScrapBucket scrapBucket = new ScrapBucket();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public QuickUnpack quickUnpack = new QuickUnpack();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public LabeledRedstoneLinkConfig labeledRedstoneLinkConfig = new LabeledRedstoneLinkConfig();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public NetworkManagerConfig networkManagerConfig = new NetworkManagerConfig();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public BatchMechanicalCrafterConfig batchMechanicalCrafterConfig = new BatchMechanicalCrafterConfig();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public WorkWarehouseConfig workWarehouseConfig = new WorkWarehouseConfig();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public ProcessManagerConfig processManagerConfig = new ProcessManagerConfig();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public PackagerAddressFilterConfig packagerAddressFilterConfig = new PackagerAddressFilterConfig();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public TemplateConfig templateConfig = new TemplateConfig();

    public static class ScrapBucket {
        public int itemsPerNugget = 64;
        public int mbPerNugget = 2000;
        public boolean generateExperienceNuggets = true;
        public String brassScrapBucketProduceItem = "create:experience_nugget";
        public int itemTransferAmount = 64;
        public int itemTransferInterval = 10;
        public int fluidTransferAmount = 1024;
        public int fluidTransferInterval = 10;
        public List<String> blacklistedItems = new ArrayList<>();
        public List<String> blacklistedFluids = new ArrayList<>();
    }

    public static class QuickUnpack {
        public boolean enabled = true;
    }

    public static class LabeledRedstoneLinkConfig {
        public boolean showFrequencyLabel = true;
    }

    public static class NetworkManagerConfig {
        @ConfigEntry.BoundedDiscrete(min = 1, max = 60)
        public int longPressThreshold = 10;
    }

    public static class BatchMechanicalCrafterConfig {
        public int maxSpeedStressImpact = 2048;
        public boolean showItemCount = true;
    }

    public static class WorkWarehouseConfig {
        public String backToConnectedInventoryAddress = "/back";
    }

    public static class ProcessManagerConfig {
        public int historyLogRetentionCount = 10;
    }

    public static class PackagerAddressFilterConfig {
        public boolean enabled = true;
    }

    public static class TemplateConfig {
        public TemplateDisplayStyle stockKeeperTemplateDisplayStyle = TemplateDisplayStyle.STYLE_1;
        public boolean mergeTemplateWithStock = false;

        /**
         * 影响仓管请求界面与红石请求器界面里，模板物品的贴图绘制方式：
         * <p>
         * STYLE_1（默认）：使用 stock_keeper_template_slot_bg.png /
         * stock_keeper_template_request_slot_bg.png 作为物品背景，鼠标悬浮时
         * 不跟随物品缩放。
         * <p>
         * STYLE_2：使用 stock_keeper_template_slot_bg2.png /
         * stock_keeper_template_request_slot_bg2.png 作为物品前景（绘制在物品
         * 图标之上），鼠标悬浮时跟随物品一起缩放。
         */
        public enum TemplateDisplayStyle {
            STYLE_1,
            STYLE_2
        }
    }
}