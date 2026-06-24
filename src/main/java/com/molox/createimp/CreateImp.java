package com.molox.createimp;

import com.molox.createimp.network.ClearNetworkSelectionPacket;
import com.molox.createimp.network.OpenLabeledRedstoneLinkGuiPacket;
import com.molox.createimp.network.OpenNetworkManagerEditPacket;
import com.molox.createimp.network.OpenNetworkManagerEditorPacket;
import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.network.SaveBrassScrapBucketConfigPacket;
import com.molox.createimp.network.SaveLabeledRedstoneLinkConfigPacket;
import com.molox.createimp.network.SaveNetworkManagerDataPacket;
import com.molox.createimp.network.SaveNetworkManagerSearchPacket;
import com.molox.createimp.network.SetNetworkSelectionPacket;
import com.molox.createimp.network.UpdateBrassScrapBucketAmountPacket;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.molox.createimp.registry.ModBlocks;
import com.molox.createimp.registry.ModCapabilities;
import com.molox.createimp.registry.ModCreativeTabs;
import com.molox.createimp.registry.ModDataComponents;
import com.molox.createimp.registry.ModItems;
import com.molox.createimp.registry.ModMenuTypes;
import com.mojang.logging.LogUtils;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(CreateImp.MODID)
public class CreateImp {
    public static final String MODID = "createimp";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateImp(IEventBus modEventBus, ModContainer modContainer) {
        AutoConfig.register(CreateImpConfig.class, GsonConfigSerializer::new);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        modEventBus.addListener(ModCapabilities::register);
        modEventBus.addListener(CreateImp::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID);
        registrar.playToServer(
                SaveBrassScrapBucketConfigPacket.TYPE,
                SaveBrassScrapBucketConfigPacket.STREAM_CODEC,
                SaveBrassScrapBucketConfigPacket::handle
        );
        registrar.playToClient(
                UpdateBrassScrapBucketAmountPacket.TYPE,
                UpdateBrassScrapBucketAmountPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.screen instanceof com.molox.createimp.screen.BrassScrapBucketScreen screen) {
                        screen.updateCurrentAmounts(packet.currentAmount(), packet.currentStacks());
                    }
                })
        );
        registrar.playToClient(
                OpenNetworkManagerGuiPacket.TYPE,
                OpenNetworkManagerGuiPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(
                        () -> com.molox.createimp.screen.NetworkManagerScreen.open(packet))
        );
        registrar.playToServer(
                SaveNetworkManagerDataPacket.TYPE,
                SaveNetworkManagerDataPacket.STREAM_CODEC,
                SaveNetworkManagerDataPacket::handle
        );
        registrar.playToServer(
                OpenNetworkManagerEditorPacket.TYPE,
                OpenNetworkManagerEditorPacket.STREAM_CODEC,
                OpenNetworkManagerEditorPacket::handle
        );
        registrar.playToServer(
                OpenNetworkManagerEditPacket.TYPE,
                OpenNetworkManagerEditPacket.STREAM_CODEC,
                OpenNetworkManagerEditPacket::handle
        );
        registrar.playToServer(
                SaveNetworkManagerSearchPacket.TYPE,
                SaveNetworkManagerSearchPacket.STREAM_CODEC,
                SaveNetworkManagerSearchPacket::handle
        );
        registrar.playToServer(
                SetNetworkSelectionPacket.TYPE,
                SetNetworkSelectionPacket.STREAM_CODEC,
                SetNetworkSelectionPacket::handle
        );
        registrar.playToServer(
                ClearNetworkSelectionPacket.TYPE,
                ClearNetworkSelectionPacket.STREAM_CODEC,
                ClearNetworkSelectionPacket::handle
        );
        registrar.playToClient(
                OpenLabeledRedstoneLinkGuiPacket.TYPE,
                OpenLabeledRedstoneLinkGuiPacket.STREAM_CODEC,
                OpenLabeledRedstoneLinkGuiPacket::handle
        );
        registrar.playToServer(
                SaveLabeledRedstoneLinkConfigPacket.TYPE,
                SaveLabeledRedstoneLinkConfigPacket.STREAM_CODEC,
                SaveLabeledRedstoneLinkConfigPacket::handle
        );
    }

    public static CreateImpConfig getConfig() {
        return AutoConfig.getConfigHolder(CreateImpConfig.class).getConfig();
    }
}