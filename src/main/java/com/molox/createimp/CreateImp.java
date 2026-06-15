package com.molox.createimp;

import com.molox.createimp.network.OpenBrassScrapBucketGuiPacket;
import com.molox.createimp.network.OpenNetworkManagerEditorPacket;
import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.network.SaveBrassScrapBucketConfigPacket;
import com.molox.createimp.network.SaveNetworkManagerDataPacket;
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
        registrar.playToClient(
                OpenBrassScrapBucketGuiPacket.TYPE,
                OpenBrassScrapBucketGuiPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() ->
                        com.molox.createimp.screen.BrassScrapBucketScreen.open(packet))
        );
        registrar.playToServer(
                SaveBrassScrapBucketConfigPacket.TYPE,
                SaveBrassScrapBucketConfigPacket.STREAM_CODEC,
                SaveBrassScrapBucketConfigPacket::handle
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
    }

    public static CreateImpConfig getConfig() {
        return AutoConfig.getConfigHolder(CreateImpConfig.class).getConfig();
    }
}