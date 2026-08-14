package com.molox.createimp;

import com.molox.createimp.client.ClientPayloadHandlers;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import java.util.function.Supplier;
import java.lang.reflect.Field;
import java.util.List;

import com.molox.createimp.item.NetworkManagerItem;
import com.molox.createimp.network.ApplyNetworkPacket;
import com.molox.createimp.network.ClearNetworkSelectionPacket;
import com.molox.createimp.network.OpenLabeledRedstoneLinkGuiPacket;
import com.molox.createimp.network.OpenNetworkManagerEditPacket;
import com.molox.createimp.network.OpenNetworkManagerEditorPacket;
import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.network.OpenProcessManagerGuiPacket;
import com.molox.createimp.network.OpenRedstoneLinkRouterGuiPacket;
import com.molox.createimp.network.OpenRedstoneLinkRouterSetItemPacket;
import com.molox.createimp.network.SaveRedstoneLinkRouterDataPacket;
import com.molox.createimp.network.SaveRedstoneLinkRouterLabelPacket;
import com.molox.createimp.network.SubmitRedstoneLinkRouterItemPacket;
import com.molox.createimp.network.RequestWorkWarehouseInterruptPacket;
import com.molox.createimp.network.OpenTemplateMaterialsGuiPacket;
import com.molox.createimp.network.OpenWorkWarehouseGuiPacket;
import com.molox.createimp.network.RequestTemplateMaterialsPacket;
import com.molox.createimp.network.RequestTemplateStockSamplePacket;
import com.molox.createimp.network.TemplateStockSampleResultPacket;
import com.molox.createimp.network.RequestWorkWarehouseAvailabilityPacket;
import com.molox.createimp.network.WorkWarehouseActivateEffectPacket;
import com.molox.createimp.network.WorkWarehouseAvailabilityPacket;
import com.molox.createimp.network.WorkWarehouseMaterialsReadyEffectPacket;
import com.molox.createimp.network.SaveBrassScrapBucketConfigPacket;
import com.molox.createimp.network.SubmitBrassScrapBucketFilterPacket;
import com.molox.createimp.network.SaveFactoryPanelDemandModePacket;
import com.molox.createimp.network.SaveLabeledRedstoneLinkConfigPacket;
import com.molox.createimp.network.SaveNetworkManagerDataPacket;
import com.molox.createimp.network.SaveNetworkManagerSearchPacket;
import com.molox.createimp.network.SaveTemplatePanelDemandModePacket;
import com.molox.createimp.network.SaveWorkWarehouseAddressPacket;
import com.molox.createimp.network.SetNetworkSelectionPacket;
import com.molox.createimp.network.TemplatePanelConfigurationPacket;
import com.molox.createimp.network.TemplatePanelConnectionPacket;
import com.molox.createimp.network.UpdateBrassScrapBucketAmountPacket;
import com.molox.createimp.block.template_panel.TemplatePanelBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseBlockEntity;
import com.molox.createimp.block.work_warehouse.WorkWarehouseUnpackingHandler;
import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkBlock;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.molox.createimp.registry.ModBlocks;
import com.molox.createimp.registry.ModCapabilities;
import com.molox.createimp.registry.ModConditions;
import com.molox.createimp.registry.ModCreativeTabs;
import com.molox.createimp.registry.ModDataComponents;
import com.molox.createimp.registry.ModItems;
import com.molox.createimp.registry.ModMenuTypes;
import com.mojang.logging.LogUtils;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.molox.createimp.block.batch_mechanical_crafter.BatchCrafterUnpackingHandler;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(CreateImp.MODID)
public class CreateImp {
    public static final String MODID = "createimp";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateImp(IEventBus modEventBus, ModContainer modContainer) {
        AutoConfig.register(CreateImpConfig.class, GsonConfigSerializer::new);
        if (FMLLoader.getDist() == Dist.CLIENT) {
            registerClientConfigVisibilityRules();
        }
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlockEntityTypes.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModConditions.CONDITION_SERIALIZERS.register(modEventBus);
        modEventBus.addListener(ModCapabilities::register);
        com.molox.createimp.block.batch_mechanical_crafter.BatchCrafterArmInteraction.register(modEventBus);
        com.molox.createimp.block.batch_repackager.BatchRepackagerArmInteraction.register(modEventBus);
        modEventBus.addListener(CreateImp::registerPayloads);
        modEventBus.addListener(CreateImp::commonSetup);
        if (FMLLoader.getDist() == Dist.CLIENT) {
            modEventBus.addListener(com.molox.createimp.client.TemplateFluidTokenClientRenderer::onRegisterClientExtensions);
        }

        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CreateImp::onRightClickBlockServer);
        // 工作仓库的"当前真实加载在世界里的实例"注册表是静态Map，只在方块
        // 被移除/所在区块被卸载时才会主动清理自己（见WorkWarehouseBlockEntity
        // 类注释）；退出世界/断开连接这个动作本身不保证会对每一个仍加载着的
        // 方块实体逐一触发这两个时机，会导致上一局残留的对象一直以强引用的
        // 形式留在Map里，表现为进程面板重进游戏后出现绑定不到任何真实仓库的
        // 幽灵/重复进程。这里订阅关卡卸载事件（断开连接、服务器关闭、切换
        // 维度等都会触发，双端都会收到），按"是否属于这个正在卸载的关卡"
        // 精确清理，不会误清其他仍在加载的维度。
        NeoForge.EVENT_BUS.addListener(WorkWarehouseBlockEntity::onLevelUnload);
    }

    /**
     * Cloth Config 的 {@code Excluded} 注解是静态的，无法依据已装模组版本
     * 动态生效。这里在客户端构建配置界面时过滤旧流体打包机去重开关；字段
     * 本身仍保留在配置数据中，以兼容已有 1.2.6 配置文件并避免影响其它流包
     * 配置项或普通 Create 打包机的独立修复开关。
     */
    private static void registerClientConfigVisibilityRules() {
        GuiRegistry registry = AutoConfig.getGuiRegistry(CreateImpConfig.class);
        registry.registerPredicateTransformer(
                (entries, i18n, field, config, defaults, access) ->
                        isLegacyFluidPackagerFixField(field)
                                && !FluidLogisticsCompat.shouldShowLegacyDuplicatePromiseFixOption()
                                ? List.of()
                                : entries,
                CreateImp::isLegacyFluidPackagerFixField
        );
    }

    private static boolean isLegacyFluidPackagerFixField(Field field) {
        return field.getDeclaringClass() == CreateImpConfig.ModCompatConfig.FluidLogisticsCompatConfig.class
                && field.getName().equals("fixFluidPackagerDuplicatePromiseConsumption");
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            UnpackingHandler.REGISTRY.register(
                    ModBlocks.BATCH_MECHANICAL_CRAFTER.get(),
                    BatchCrafterUnpackingHandler.INSTANCE
            );
            UnpackingHandler.REGISTRY.register(
                    ModBlocks.WORK_WAREHOUSE.get(),
                    WorkWarehouseUnpackingHandler.INSTANCE
            );
            // 修复打包机无法把废料桶识别为容器、解包进废料桶摧毁的问题——见
            // AndesiteScrapBucketUnpackingHandler/BrassScrapBucketUnpackingHandler
            // 上的说明。这个注册表是 Create 公开的通用注册表，流体包裹的拆包机
            // （FluidRepackagerBlockEntity）处理包裹里"普通物品"部分时也是查的
            // 同一个注册表，因此这里顺带修好了"解包机解包固液混合包裹到废料桶"
            // 这个兼容需求里物品的那一部分，不需要为流体包裹另外写一份。
            UnpackingHandler.REGISTRY.register(
                    ModBlocks.ANDESITE_SCRAP_BUCKET.get(),
                    com.molox.createimp.block.andesite_scrap_bucket.AndesiteScrapBucketUnpackingHandler.INSTANCE
            );
            UnpackingHandler.REGISTRY.register(
                    ModBlocks.BRASS_SCRAP_BUCKET.get(),
                    com.molox.createimp.block.brass_scrap_bucket.BrassScrapBucketUnpackingHandler.INSTANCE
            );
            BlockStressValues.IMPACTS.register(
                    ModBlocks.BATCH_MECHANICAL_CRAFTER.get(),
                    () -> CreateImp.getConfig().batchMechanicalCrafterConfig.maxSpeedStressImpact / 256.0
            );
            // 标码无线红石信号终端的朝向语义与原版无线红石信号终端（RedstoneLinkBlock）
            // 完全一致（附着在facing.getOpposite()方向的方块上），但Create自身判断
            // "这个方块是否附着在邻居上"的逻辑（isBlockAttachedTowardsFallback）是按
            // 具体类型instanceof RedstoneLinkBlock硬编码识别的，我们的方块不继承该类，
            // 不会被识别为附着方块，导致动态结构（旋转轴承等）组装时把它当成普通
            // 自由方块处理，进而在支撑方块被移出世界的过程中，我们自己的canSurvive
            // 判断被正常的红石更新链路触发、误判支撑消失并调用destroyBlock掉落自身，
            // 而动态结构已经把这个终端的状态快照捕获了进去——同一个方块因此被复制。
            // 这里向Create补充一条针对本方块的附着识别规则，判断条件与原版终端完全
            // 一致，只是让动态结构组装"认识"这个方块、按正确顺序处理，不改动本方块
            // 自身任何逻辑，也不影响其它任何方块的动态结构行为。
            BlockMovementChecks.registerAttachedCheck((state, world, pos, direction) -> {
                if (!(state.getBlock() instanceof LabeledRedstoneLinkBlock)) {
                    return BlockMovementChecks.CheckResult.PASS;
                }
                return BlockMovementChecks.CheckResult.of(
                        direction.getOpposite() == state.getValue(LabeledRedstoneLinkBlock.FACING));
            });
            // 让流体包裹指读棒"自动连接动力合成器"这一功能对本模组批量动力
            // 合成器同样生效——只兼容不依赖，未安装流体包裹时这里直接跳过，
            // 不会触发流体包裹任何类的加载。不受配置项控制，装了就一直生效。
            if (com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat.isLoaded()) {
                com.molox.createimp.compat.fluidlogistics.BatchCrafterHandPointerAdapter.register();
            }
        });
    }

    private static void onRightClickBlockServer(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide() != LogicalSide.SERVER) return;

        net.minecraft.world.entity.player.Player player = event.getEntity();
        if (player.isShiftKeyDown()) return;

        ItemStack stack = player.getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof NetworkManagerItem)) return;
        if (!stack.has(ModDataComponents.NETWORK_SELECTED_STATE.get())) return;

        BlockEntity be = player.level().getBlockEntity(event.getPos());
        if (be == null) return;

        boolean isTarget = NetworkManagerItem.getBehaviour(be) != null
                || be instanceof FactoryPanelBlockEntity
                || be instanceof TemplatePanelBlockEntity;
        if (!isTarget) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID);
        registrar.playToServer(
                SaveBrassScrapBucketConfigPacket.TYPE,
                SaveBrassScrapBucketConfigPacket.STREAM_CODEC,
                SaveBrassScrapBucketConfigPacket::handle
        );
        registrar.playToServer(
                SubmitBrassScrapBucketFilterPacket.TYPE,
                SubmitBrassScrapBucketFilterPacket.STREAM_CODEC,
                SubmitBrassScrapBucketFilterPacket::handle
        );
        registrar.playToClient(
                UpdateBrassScrapBucketAmountPacket.TYPE,
                UpdateBrassScrapBucketAmountPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToClient(
                OpenNetworkManagerGuiPacket.TYPE,
                OpenNetworkManagerGuiPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
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
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToServer(
                SaveLabeledRedstoneLinkConfigPacket.TYPE,
                SaveLabeledRedstoneLinkConfigPacket.STREAM_CODEC,
                SaveLabeledRedstoneLinkConfigPacket::handle
        );
        registrar.playToServer(
                ApplyNetworkPacket.TYPE,
                ApplyNetworkPacket.STREAM_CODEC,
                ApplyNetworkPacket::handle
        );
        registrar.playToServer(
                SaveFactoryPanelDemandModePacket.TYPE,
                SaveFactoryPanelDemandModePacket.STREAM_CODEC,
                SaveFactoryPanelDemandModePacket::handle
        );
        registrar.playToServer(
                TemplatePanelConnectionPacket.TYPE,
                TemplatePanelConnectionPacket.STREAM_CODEC,
                TemplatePanelConnectionPacket::handle
        );
        registrar.playToServer(
                TemplatePanelConfigurationPacket.TYPE,
                TemplatePanelConfigurationPacket.STREAM_CODEC,
                TemplatePanelConfigurationPacket::handle
        );
        registrar.playToServer(
                SaveTemplatePanelDemandModePacket.TYPE,
                SaveTemplatePanelDemandModePacket.STREAM_CODEC,
                SaveTemplatePanelDemandModePacket::handle
        );
        registrar.playToClient(
                OpenWorkWarehouseGuiPacket.TYPE,
                OpenWorkWarehouseGuiPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToServer(
                SaveWorkWarehouseAddressPacket.TYPE,
                SaveWorkWarehouseAddressPacket.STREAM_CODEC,
                SaveWorkWarehouseAddressPacket::handle
        );
        registrar.playToClient(
                WorkWarehouseActivateEffectPacket.TYPE,
                WorkWarehouseActivateEffectPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToClient(
                WorkWarehouseMaterialsReadyEffectPacket.TYPE,
                WorkWarehouseMaterialsReadyEffectPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToServer(
                RequestTemplateMaterialsPacket.TYPE,
                RequestTemplateMaterialsPacket.STREAM_CODEC,
                RequestTemplateMaterialsPacket::handle
        );
        registrar.playToClient(
                OpenTemplateMaterialsGuiPacket.TYPE,
                OpenTemplateMaterialsGuiPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToClient(
                OpenProcessManagerGuiPacket.TYPE,
                OpenProcessManagerGuiPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToServer(
                RequestWorkWarehouseInterruptPacket.TYPE,
                RequestWorkWarehouseInterruptPacket.STREAM_CODEC,
                RequestWorkWarehouseInterruptPacket::handle
        );
        registrar.playToServer(
                RequestWorkWarehouseAvailabilityPacket.TYPE,
                RequestWorkWarehouseAvailabilityPacket.STREAM_CODEC,
                RequestWorkWarehouseAvailabilityPacket::handle
        );
        registrar.playToClient(
                WorkWarehouseAvailabilityPacket.TYPE,
                WorkWarehouseAvailabilityPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToServer(
                RequestTemplateStockSamplePacket.TYPE,
                RequestTemplateStockSamplePacket.STREAM_CODEC,
                RequestTemplateStockSamplePacket::handle
        );
        registrar.playToClient(
                TemplateStockSampleResultPacket.TYPE,
                TemplateStockSampleResultPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToClient(
                OpenRedstoneLinkRouterGuiPacket.TYPE,
                OpenRedstoneLinkRouterGuiPacket.STREAM_CODEC,
                clientHandler(() -> ClientPayloadHandlers::handle)
        );
        registrar.playToServer(
                SaveRedstoneLinkRouterDataPacket.TYPE,
                SaveRedstoneLinkRouterDataPacket.STREAM_CODEC,
                SaveRedstoneLinkRouterDataPacket::handle
        );
        registrar.playToServer(
                OpenRedstoneLinkRouterSetItemPacket.TYPE,
                OpenRedstoneLinkRouterSetItemPacket.STREAM_CODEC,
                OpenRedstoneLinkRouterSetItemPacket::handle
        );
        registrar.playToServer(
                SubmitRedstoneLinkRouterItemPacket.TYPE,
                SubmitRedstoneLinkRouterItemPacket.STREAM_CODEC,
                SubmitRedstoneLinkRouterItemPacket::handle
        );
        registrar.playToServer(
                SaveRedstoneLinkRouterLabelPacket.TYPE,
                SaveRedstoneLinkRouterLabelPacket.STREAM_CODEC,
                SaveRedstoneLinkRouterLabelPacket::handle
        );
    }

    /**
     * 只有在真正运行于客户端时才会调用 supplier.get() 来创建客户端专属的
     * IPayloadHandler（例如引用 ClientPayloadHandlers 的方法引用）。
     * 在专用服务器上，supplier.get() 永远不会被执行到，client 专属类
     * 因此也永远不会在服务端被加载/触发 invokedynamic 引导，从而避免
     * RuntimeDistCleaner 报错。写法照抄自 Create 自己的 DistExecutor
     * （com.simibubi.create.foundation.utility.DistExecutor#unsafeCallWhenOn）。
     */
    private static <T extends CustomPacketPayload> IPayloadHandler<T> clientHandler(Supplier<IPayloadHandler<T>> supplier) {
        if (FMLLoader.getDist() == Dist.CLIENT) {
            return supplier.get();
        }
        return (payload, context) -> {};
    }

    public static CreateImpConfig getConfig() {
        return AutoConfig.getConfigHolder(CreateImpConfig.class).getConfig();
    }
}
