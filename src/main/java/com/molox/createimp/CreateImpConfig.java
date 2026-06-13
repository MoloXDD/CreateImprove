package com.molox.createimp;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "createimp")
public class CreateImpConfig implements ConfigData {

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public BrassScrapBucket brassScrapBucket = new BrassScrapBucket();

    public static class BrassScrapBucket {
        public int itemsPerNugget = 64;
        public int mbPerNugget = 2000;
    }
}