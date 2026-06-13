package com.molox.createimp;

import com.molox.createimp.registry.ModItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import me.shedaniel.autoconfig.AutoConfig;
import net.createmod.catnip.lang.FontHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CreateImp.MODID, dist = Dist.CLIENT)
public class CreateImpClient {

    public CreateImpClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, screen) ->
                        AutoConfig.getConfigScreen(CreateImpConfig.class, screen).get());

        modEventBus.addListener(CreateImpClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        TooltipModifier.REGISTRY.register(
                ModItems.ANDESITE_SCRAP_BUCKET.get().asItem(),
                new ItemDescription.Modifier(ModItems.ANDESITE_SCRAP_BUCKET.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );
        TooltipModifier.REGISTRY.register(
                ModItems.BRASS_SCRAP_BUCKET.get().asItem(),
                new ItemDescription.Modifier(ModItems.BRASS_SCRAP_BUCKET.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );
    }
}