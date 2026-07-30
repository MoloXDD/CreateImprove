package com.molox.createimp.client;

import com.molox.createimp.CreateImp;
import com.molox.createimp.network.OpenLabeledRedstoneLinkGuiPacket;
import com.molox.createimp.network.OpenNetworkManagerGuiPacket;
import com.molox.createimp.network.OpenProcessManagerGuiPacket;
import com.molox.createimp.network.OpenRedstoneLinkRouterGuiPacket;
import com.molox.createimp.network.OpenTemplateMaterialsGuiPacket;
import com.molox.createimp.network.OpenWorkWarehouseGuiPacket;
import com.molox.createimp.network.TemplateStockSampleResultPacket;
import com.molox.createimp.network.UpdateBrassScrapBucketAmountPacket;
import com.molox.createimp.network.WorkWarehouseActivateEffectPacket;
import com.molox.createimp.network.WorkWarehouseAvailabilityPacket;
import com.molox.createimp.network.WorkWarehouseMaterialsReadyEffectPacket;
import com.molox.createimp.screen.BrassScrapBucketScreen;
import com.molox.createimp.screen.LabeledRedstoneLinkScreen;
import com.molox.createimp.screen.NetworkManagerScreen;
import com.molox.createimp.screen.ProcessManagerScreen;
import com.molox.createimp.screen.RedstoneLinkRouterScreen;
import com.molox.createimp.screen.TemplateMaterialsScreen;
import com.molox.createimp.screen.WorkWarehouseScreen;
import com.molox.createimp.util.StockKeeperRequestScreenInvoker;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.WiFiParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 集中存放所有 playToClient 注册的客户端专属处理逻辑。
 * <p>
 * 这是一个独立类，不依附于任何 Payload 记录类本身——Payload 记录类
 * 自身（TYPE/STREAM_CODEC/type()）在专用服务器上也必须能正常加载
 * （发送数据包时需要），如果客户端专属逻辑写在 Payload 记录类自己
 * 身上，服务端加载这个类时会连带验证到这些逻辑，触发
 * "Attempted to load class ... for invalid dist DEDICATED_SERVER"。
 * 这个类没有其他理由在服务端被加载，服务端也永远不会调用这里的方法，
 * 所以不需要也不应该加 {@code @OnlyIn(Dist.CLIENT)}——这个写法完全
 * 照抄自 NeoForge 官方 {@code net.neoforged.neoforge.network.handlers.ClientPayloadHandler}。
 */
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {}

    public static void handle(UpdateBrassScrapBucketAmountPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof BrassScrapBucketScreen screen) {
                screen.updateCurrentAmounts(packet.currentAmount(), packet.currentStacks());
            }
        });
    }

    public static void handle(OpenNetworkManagerGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> NetworkManagerScreen.open(packet));
    }

    public static void handle(OpenProcessManagerGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ProcessManagerScreen.open(packet));
    }

    public static void handle(OpenRedstoneLinkRouterGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> RedstoneLinkRouterScreen.open(packet));
    }

    public static void handle(OpenWorkWarehouseGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> WorkWarehouseScreen.open(packet));
    }

    public static void handle(WorkWarehouseActivateEffectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            Vec3 center = Vec3.atCenterOf(packet.pos());
            AllSoundEvents.CONFIRM.playAt(level, center, 0.5f, 1.5f, false);
            AllSoundEvents.STOCK_LINK.playAt(level, center, 1.0f, 1.0f, false);
            level.addParticle(new WiFiParticle.Data(), center.x, center.y, center.z, 1.0, 1.0, 1.0);
        });
    }

    public static void handle(WorkWarehouseMaterialsReadyEffectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            Vec3 center = Vec3.atCenterOf(packet.pos());
            // 与工厂仪表检测物品数量满足需求时（FactoryPanelBehaviour 中
            // !satisfied && shouldSatisfy 分支）播放的同一组音效。
            AllSoundEvents.CONFIRM.playAt(level, center, 0.075f, 1.0f, false);
            AllSoundEvents.CONFIRM_2.playAt(level, center, 0.125f, 0.575f, false);
            level.addParticle(new WiFiParticle.Data(), center.x, center.y, center.z, 1.0, 1.0, 1.0);
        });
    }

    public static void handle(OpenLabeledRedstoneLinkGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> LabeledRedstoneLinkScreen.open(packet));
    }

    public static void handle(OpenTemplateMaterialsGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            CreateImp.LOGGER.info(
                    "[模板材料] 客户端收到材料计算结果：网络={}, 模板数={}, 能否完全满足={}, 链是否失效={}",
                    packet.freqId(), packet.templateCount(),
                    packet.completionState().canCompleteAll(), packet.completionState().anyChainBroken());
            if (packet.completionState().anyChainBroken()) {
                if (Minecraft.getInstance().screen instanceof TemplateMaterialsScreen existing) {
                    existing.handleChainBroken();
                } else if (Minecraft.getInstance().screen instanceof StockKeeperRequestScreenInvoker invoker) {
                    invoker.createimp$clearRequestBar();
                }
                return;
            }
            if (Minecraft.getInstance().screen instanceof TemplateMaterialsScreen existing) {
                existing.applyResult(packet);
            } else {
                TemplateMaterialsScreen.open(packet);
            }
        });
    }

    public static void handle(WorkWarehouseAvailabilityPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                ClientWorkWarehouseAvailabilityCache.update(packet.freqId(), packet.availableCount()));
    }

    public static void handle(TemplateStockSampleResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof TemplateMaterialsScreen screen) {
                screen.applySampleCounts(packet.counts());
            }
        });
    }
}