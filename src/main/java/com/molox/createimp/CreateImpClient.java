package com.molox.createimp;

import com.molox.createimp.block.batch_mechanical_crafter.BatchMechanicalCrafterRenderer;
import com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketRenderer;
import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkRenderer;
import com.molox.createimp.client.NetworkManagerClientHandler;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.molox.createimp.registry.ModBlocks;
import com.molox.createimp.registry.ModItems;
import com.molox.createimp.registry.ModMenuTypes;
import com.molox.createimp.screen.BrassScrapBucketScreen;
import com.molox.createimp.screen.NetworkManagerLabelEditScreen;
import com.molox.createimp.screen.NetworkManagerLabelEditorScreen;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.molox.createimp.block.batch_mechanical_crafter.BatchCrafterCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import me.shedaniel.autoconfig.AutoConfig;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
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

        NeoForge.EVENT_BUS.addListener(CreateImpClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CreateImpClient::onRightClickBlock);
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
        TooltipModifier.REGISTRY.register(
                ModItems.LABELED_REDSTONE_LINK.get().asItem(),
                new ItemDescription.Modifier(ModItems.LABELED_REDSTONE_LINK.get().asItem(), FontHelper.Palette.STANDARD_CREATE)
        );

        // 批量动力合成器连接纹理
        CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "batch_mechanical_crafter");
            com.simibubi.create.CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                    .register(id, model -> new CTModel(model, new BatchCrafterCTBehaviour()));
        });

        // 批量动力合成器 Flywheel 齿轮旋转 Visual
        event.enqueueWork(() ->
                SimpleBlockEntityVisualizer.builder(ModBlockEntityTypes.BATCH_MECHANICAL_CRAFTER.get())
                        .factory(SingleAxisRotatingVisual.of(AllPartialModels.SHAFTLESS_COGWHEEL))
                        .skipVanillaRender(be -> false)
                        .apply()
        );
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.NETWORK_MANAGER_LABEL_EDITOR.get(),
                NetworkManagerLabelEditorScreen::new);
        event.register(ModMenuTypes.NETWORK_MANAGER_LABEL_EDIT.get(),
                NetworkManagerLabelEditScreen::new);
        event.register(ModMenuTypes.BRASS_SCRAP_BUCKET.get(),
                BrassScrapBucketScreen::new);
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

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        NetworkManagerClientHandler.onRightClickBlock(event);
    }
}