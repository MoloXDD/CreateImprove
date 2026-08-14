package com.molox.createimp;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

@Config(name = "createimp")
public class CreateImpConfig implements ConfigData {

    @ConfigEntry.Category("functionConfig")
    @ConfigEntry.Gui.TransitiveObject
    public FunctionConfig functionConfig = new FunctionConfig();

    @ConfigEntry.Category("scrapBucket")
    @ConfigEntry.Gui.TransitiveObject
    public ScrapBucket scrapBucket = new ScrapBucket();

    @ConfigEntry.Category("labeledRedstoneLinkConfig")
    @ConfigEntry.Gui.TransitiveObject
    public LabeledRedstoneLinkConfig labeledRedstoneLinkConfig = new LabeledRedstoneLinkConfig();

    @ConfigEntry.Category("networkManagerConfig")
    @ConfigEntry.Gui.TransitiveObject
    public NetworkManagerConfig networkManagerConfig = new NetworkManagerConfig();

    @ConfigEntry.Category("batchMechanicalCrafterConfig")
    @ConfigEntry.Gui.TransitiveObject
    public BatchMechanicalCrafterConfig batchMechanicalCrafterConfig = new BatchMechanicalCrafterConfig();

    @ConfigEntry.Category("templateFunctionConfig")
    @ConfigEntry.Gui.TransitiveObject
    public TemplateFunctionConfig templateFunctionConfig = new TemplateFunctionConfig();

    @ConfigEntry.Category("fixConfig")
    @ConfigEntry.Gui.TransitiveObject
    public FixConfig fixConfig = new FixConfig();

    @ConfigEntry.Category("modCompatConfig")
    @ConfigEntry.Gui.TransitiveObject
    public ModCompatConfig modCompatConfig = new ModCompatConfig();

    public static class FunctionConfig {
        @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
        public ItemToggles itemToggles = new ItemToggles();

        @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
        public FeatureToggles featureToggles = new FeatureToggles();

        public static class ItemToggles {
            @ConfigEntry.Gui.RequiresRestart
            public boolean andesiteScrapBucketEnabled = true;
            @ConfigEntry.Gui.RequiresRestart
            public boolean brassScrapBucketEnabled = true;
            @ConfigEntry.Gui.RequiresRestart
            public boolean networkManagerEnabled = true;
            @ConfigEntry.Gui.RequiresRestart
            public boolean labeledRedstoneLinkEnabled = true;
            @ConfigEntry.Gui.RequiresRestart
            public boolean batchCraftingEnabled = true;
            @ConfigEntry.Gui.RequiresRestart
            public boolean templateSystemEnabled = true;
            @ConfigEntry.Gui.RequiresRestart
            public boolean redstoneLinkRouterEnabled = true;
        }

        public static class FeatureToggles {
            public boolean packagerAddressFilterEnabled = true;
            public boolean quickUnpackEnabled = true;
            public boolean factoryDemandModeEnabled = true;
            public boolean templateDemandModeEnabled = true;
        }
    }

    public static class ScrapBucket {
        public int itemsPerNugget = 64;
        public int mbPerNugget = 8000;
        public List<ItemProductionEfficiency> itemProductionEfficiencies = new ArrayList<>();
        public List<FluidProductionEfficiency> fluidProductionEfficiencies = new ArrayList<>();
        public boolean generateExperienceNuggets = true;
        public String brassScrapBucketProduceItem = "create:experience_nugget";
        public int itemTransferAmount = 64;
        public int itemTransferInterval = 10;
        public int fluidTransferAmount = 1024;
        public int fluidTransferInterval = 10;
        public List<String> blacklistedItems = new ArrayList<>();
        public List<String> blacklistedFluids = new ArrayList<>();

        public static class ItemProductionEfficiency {
            public String itemId = "";
            public int itemsPerNugget = 64;
        }

        public static class FluidProductionEfficiency {
            public String fluidId = "";
            public int mbPerNugget = 8000;
        }
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

    public static class TemplateFunctionConfig {
        public String backToConnectedInventoryAddress = "/back";
        /**
         * 范围限定在 1~10：这个数值和工作仓库单次工作日志的硬性字节上限
         * （{@code WorkWarehouseBlockEntity.LOG_ENTRIES_HARD_CAP_BYTES}，
         * 150KB）是配合生效的——进程面板会把这里设定的份数一起塞进同一个
         * 方块实体同步包，如果这个值不设上限，即便单条日志本身有硬顶，
         * 总量乘起来依然可能突破客户端 NBT 解码的硬性配额（2MiB）导致崩溃。
         * 10 这个上限对应"10 × 150KB = 1.5MB"，比 2MiB 留了安全余量。
         */
        @ConfigEntry.BoundedDiscrete(min = 1, max = 10)
        public int historyLogRetentionCount = 10;
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

    public static class FixConfig {
        /**
         * Create原版打包机在检测"库存新增了多少"以扣减承诺队列时，是按每个打包机
         * 各自私有的库存快照独立计算的；当多个打包机贴着同一个物理仓库时，每个
         * 打包机都会独立观察到同一次入库，导致同一批到货被重复扣减多次承诺，
         * 使得按量请求/补货等依赖承诺判断的功能误判缺口、反复超发请求。
         * <p>
         * 关闭此项后，恢复Create原版行为（不做去重），保留作为排查用的回退开关。
         */
        public boolean fixDuplicatePackagerPromiseConsumption = true;
    }

    public static class ModCompatConfig {
        @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
        public FluidLogisticsCompatConfig fluidLogisticsCompat = new FluidLogisticsCompatConfig();

        public static class FluidLogisticsCompatConfig {
            /**
             * 流体打包机（流体包裹自己的 {@code FluidPackagerBlockEntity}）同样
             * 存在"多台打包机贴着同一个物理仓库时，各自独立观察到同一次入库，
             * 导致同一批到货被重复扣减多次承诺"的问题，原理与
             * {@link FixConfig#fixDuplicatePackagerPromiseConsumption} 描述的
             * Create 原版问题完全一致，只是流体包裹这一侧的到货通知走的是它
             * 自己独立的一套实现，需要单独修复、单独开关。
             * <p>
             * 关闭此项后，恢复流体包裹原版行为（不做去重）。
             */
            public boolean fixFluidPackagerDuplicatePromiseConsumption = true;

            /**
             * 出库地址过滤功能（{@link FunctionConfig.FeatureToggles
             * #packagerAddressFilterEnabled}）对流体打包机单独生效的开关——
             * 挑选打包机时，如果候选打包机是流体打包机，改用这个开关判断是否
             * 按告示牌地址过滤，判断算法与普通打包机完全一致，只是开关分开，
             * 方便单独排查流体这一侧的问题而不影响物品那一侧。
             * <p>
             * 关闭此项后，流体打包机的挑选退回原版随机（不影响普通打包机）。
             */
            public boolean fluidPackagerAddressFilterEnabled = true;
        }
    }
}
