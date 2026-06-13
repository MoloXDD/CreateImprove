package com.molox.createimp;

import me.shedaniel.autoconfig.AutoConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CreateImp.MODID, dist = Dist.CLIENT)
public class CreateImpClient {

    public CreateImpClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, screen) ->
                        AutoConfig.getConfigScreen(CreateImpConfig.class, screen).get());
    }
}