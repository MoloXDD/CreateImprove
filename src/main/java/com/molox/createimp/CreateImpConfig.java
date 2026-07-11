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

    public static class ScrapBucket {
        public int itemsPerNugget = 64;
        public int mbPerNugget = 2000;
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
}