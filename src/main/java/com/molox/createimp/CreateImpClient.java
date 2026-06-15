package com.molox.createimp;

import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketRenderer;
import com.molox.createimp.client.NetworkManagerClientHandler;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.molox.createimp.registry.ModItems;
import com.molox.createimp.registry.ModMenuTypes;
import com.molox.createimp.screen.NetworkManagerLabelEditorScreen;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import me.shedaniel.autoconfig.AutoConfig;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CreateImp.MODID, dist = Dist.CLIENT)
public class CreateImpClient {

    public CreateImpClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, screen) ->
                        AutoConfig.getConfigScreen(CreateImpConfig.class, screen).get());

        modEventBus.addListener(CreateImpClient::onClientSetup);
        modEventBus.addListener(CreateImpClient::onRegisterRenderers);
        modEventBus.addListener(CreateImpClient::onRegisterMenuScreens);
        modEventBus.addListener(CreateImpClient::onRegisterGuiLayers);

        // 用 Pre 而不是 Post：
        // Create 的 LogisticallyLinkedClientHandler.tick() 注册在 Pre，我们后注册所以后执行
        // 顺序：Create 清空并重新设置 previouslyHeldFrequency（null）→ 我们覆盖为选中 UUID
        // → BE tick → FactoryPanelBehaviour.tickPanel() 读到正确值
        NeoForge.EVENT_BUS.addListener(CreateImpClient::onClientTick);
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
        TooltipModifier.REGISTRY.register(
                ModItems.NETWORK_MANAGER.get().asItem(),
                new ItemDescription.Modifier(ModItems.NETWORK_MANAGER.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.NETWORK_MANAGER_LABEL_EDITOR.get(),
                NetworkManagerLabelEditorScreen::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntityTypes.BRASS_SCRAP_BUCKET.get(),
                BrassScrapBucketRenderer::new
        );
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "network_manager_hud"),
                (guiGraphics, deltaTracker) ->
                        NetworkManagerClientHandler.renderHud(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false))
        );
    }

    private static void onClientTick(ClientTickEvent.Pre event) {
        NetworkManagerClientHandler.tick();
    }
}