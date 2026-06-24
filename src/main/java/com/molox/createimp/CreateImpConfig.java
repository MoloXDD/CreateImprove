package com.molox.createimp;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "createimp")
public class CreateImpConfig implements ConfigData {

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public BrassScrapBucket brassScrapBucket = new BrassScrapBucket();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public QuickUnpack quickUnpack = new QuickUnpack();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public LabeledRedstoneLinkConfig labeledRedstoneLinkConfig = new LabeledRedstoneLinkConfig();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public NetworkManagerConfig networkManagerConfig = new NetworkManagerConfig();

    public static class BrassScrapBucket {
        public int itemsPerNugget = 64;
        public int mbPerNugget = 2000;
        public int itemTransferAmount = 64;
        public int itemTransferInterval = 10;
        public int fluidTransferAmount = 1024;
        public int fluidTransferInterval = 10;
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
}