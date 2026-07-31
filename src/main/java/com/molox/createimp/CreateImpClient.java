package com.molox.createimp;

import com.molox.createimp.block.batch_mechanical_crafter.BatchMechanicalCrafterRenderer;
import com.molox.createimp.block.batch_repackager.BatchRepackagerRenderer;
import com.molox.createimp.block.batch_repackager.BatchRepackagerVisual;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketRenderer;
import com.molox.createimp.client.BrassScrapBucketTooltipModifier;
import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkRenderer;
import com.molox.createimp.block.template_panel.TemplatePanelConnectionHandler;
import com.molox.createimp.block.template_panel.TemplatePanelModel;
import com.molox.createimp.block.template_panel.TemplatePanelRenderer;
import com.molox.createimp.client.NetworkManagerClientHandler;
import com.molox.createimp.client.TemplateOrderTooltipHandler;
import com.molox.createimp.client.WorkWarehouseAvailabilityPollHandler;
import com.molox.createimp.ponder.CreateImpPonderPlugin;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.molox.createimp.registry.ModBlocks;
import com.molox.createimp.registry.ModItems;
import com.molox.createimp.registry.ModMenuTypes;
import com.molox.createimp.screen.BrassScrapBucketScreen;
import com.molox.createimp.screen.NetworkManagerLabelEditScreen;
import com.molox.createimp.screen.NetworkManagerLabelEditorScreen;
import com.molox.createimp.screen.RedstoneLinkRouterSetItemScreen;
import com.molox.createimp.screen.TemplatePanelSetItemScreen;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.molox.createimp.client.BatchCrafterCTQuadLibrary;
import com.molox.createimp.client.BatchCrafterUnbakedGeometry;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import me.shedaniel.autoconfig.AutoConfig;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

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
        modEventBus.addListener(CreateImpClient::onRegisterAdditionalModels);
        modEventBus.addListener(CreateImpClient::onModifyBakingResult);
        modEventBus.addListener(CreateImpClient::onRegisterGeometryLoaders);

        NeoForge.EVENT_BUS.addListener(CreateImpClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CreateImpClient::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CreateImpClient::onRightClickBlockTemplatePanel);
        NeoForge.EVENT_BUS.addListener(TemplateOrderTooltipHandler::onItemTooltip);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        com.molox.createimp.block.template_panel.TemplatePanelModel.init();

        PonderIndex.addPlugin(new CreateImpPonderPlugin());

        TooltipModifier.REGISTRY.register(
                ModItems.ANDESITE_SCRAP_BUCKET.get().asItem(),
                new ItemDescription.Modifier(ModItems.ANDESITE_SCRAP_BUCKET.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );
        TooltipModifier.REGISTRY.register(
                ModItems.BRASS_SCRAP_BUCKET.get().asItem(),
                new BrassScrapBucketTooltipModifier(FontHelper.Palette.STANDARD_CREATE)
        );
        TooltipModifier.REGISTRY.register(
                ModItems.NETWORK_MANAGER.get().asItem(),
                new ItemDescription.Modifier(ModItems.NETWORK_MANAGER.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );
        TooltipModifier.REGISTRY.register(
                ModItems.LABELED_REDSTONE_LINK.get().asItem(),
                new ItemDescription.Modifier(ModItems.LABELED_REDSTONE_LINK.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );
        TooltipModifier.REGISTRY.register(
                ModItems.BATCH_REPACKAGER.get().asItem(),
                new ItemDescription.Modifier(ModItems.BATCH_REPACKAGER.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );
        TooltipModifier.REGISTRY.register(
                ModItems.BATCH_MECHANICAL_CRAFTER.get().asItem(),
                new ItemDescription.Modifier(ModItems.BATCH_MECHANICAL_CRAFTER.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );
        TooltipModifier.REGISTRY.register(
                ModItems.REDSTONE_LINK_ROUTER.get().asItem(),
                new ItemDescription.Modifier(ModItems.REDSTONE_LINK_ROUTER.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );

        CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> {
            ResourceLocation templatePanelId = ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "template_panel");
            com.simibubi.create.CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                    .register(templatePanelId, TemplatePanelModel::new);
        });

        event.enqueueWork(() -> {
            SimpleBlockEntityVisualizer.builder(ModBlockEntityTypes.BATCH_MECHANICAL_CRAFTER.get())
                    .factory(SingleAxisRotatingVisual.of(AllPartialModels.SHAFTLESS_COGWHEEL))
                    .skipVanillaRender(be -> false)
                    .apply();
            SimpleBlockEntityVisualizer.builder(ModBlockEntityTypes.BATCH_REPACKAGER.get())
                    .factory(BatchRepackagerVisual::new)
                    .skipVanillaRender(be -> false)
                    .apply();
        });
    }

    private static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(BatchCrafterUnbakedGeometry.Loader.ID, BatchCrafterUnbakedGeometry.Loader.INSTANCE);
    }

    private static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(BatchCrafterCTQuadLibrary.LIBRARY_MODEL));
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var libraryModel = event.getModels().get(ModelResourceLocation.standalone(BatchCrafterCTQuadLibrary.LIBRARY_MODEL));
        if (libraryModel == null) {
            CreateImp.LOGGER.error("未能找到批量动力合成器连接纹理辅助模型(ct_library.json)，连接纹理替换将被跳过");
            return;
        }
        BatchCrafterCTQuadLibrary.init(libraryModel);
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.NETWORK_MANAGER_LABEL_EDITOR.get(),
                NetworkManagerLabelEditorScreen::new);
        event.register(ModMenuTypes.NETWORK_MANAGER_LABEL_EDIT.get(),
                NetworkManagerLabelEditScreen::new);
        event.register(ModMenuTypes.BRASS_SCRAP_BUCKET.get(),
                BrassScrapBucketScreen::new);
        event.register(ModMenuTypes.TEMPLATE_PANEL_SET_ITEM.get(),
                TemplatePanelSetItemScreen::new);
        event.register(ModMenuTypes.REDSTONE_LINK_ROUTER_SET_ITEM.get(),
                RedstoneLinkRouterSetItemScreen::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntityTypes.BRASS_SCRAP_BUCKET.get(),
                BrassScrapBucketRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntityTypes.LABELED_REDSTONE_LINK.get(),
                LabeledRedstoneLinkRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntityTypes.BATCH_MECHANICAL_CRAFTER.get(),
                BatchMechanicalCrafterRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntityTypes.BATCH_REPACKAGER.get(),
                BatchRepackagerRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntityTypes.TEMPLATE_PANEL.get(),
                TemplatePanelRenderer::new
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
        TemplatePanelConnectionHandler.clientTick();
        WorkWarehouseAvailabilityPollHandler.tick();
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        NetworkManagerClientHandler.onRightClickBlock(event);
    }

    private static void onRightClickBlockTemplatePanel(PlayerInteractEvent.RightClickBlock event) {
        TemplatePanelConnectionHandler.onRightClick(event);
    }
}