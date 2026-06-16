package com.molox.createimp.block.labeled_redstone_link;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

public class LabeledRedstoneLinkNetworkHandler {

    private static final Map<ServerLevel, LabeledRedstoneLinkNetworkHandler> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    // 按频率分组的所有终端（发送端+接收端都在里面）
    private final Map<String, Set<LabeledRedstoneLinkBlockEntity>> networks = new HashMap<>();

    private LabeledRedstoneLinkNetworkHandler() {}

    public static LabeledRedstoneLinkNetworkHandler get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return INSTANCES.computeIfAbsent(serverLevel, k -> new LabeledRedstoneLinkNetworkHandler());
        }
        return null;
    }

    public void addToNetwork(LabeledRedstoneLinkBlockEntity be) {
        String freq = be.getFrequencyText();
        networks.computeIfAbsent(freq, k -> new HashSet<>()).add(be);
    }

    public void removeFromNetwork(LabeledRedstoneLinkBlockEntity be) {
        String freq = be.getFrequencyText();
        Set<LabeledRedstoneLinkBlockEntity> group = networks.get(freq);
        if (group != null) {
            group.remove(be);
            if (group.isEmpty()) networks.remove(freq);
        }
    }

    /**
     * 某发送端信号变化后，更新同频率所有接收端。
     * 完全对应原版 RedstoneLinkNetworkHandler.updateNetworkOf
     */
    public void updateNetworkOf(LabeledRedstoneLinkBlockEntity transmitter) {
        String freq = transmitter.getFrequencyText();
        updateAll(freq);
    }

    public void updateAll(String frequency) {
        Set<LabeledRedstoneLinkBlockEntity> group = networks.get(frequency);
        if (group == null || group.isEmpty()) return;

        // 取所有发送端的最大功率
        int maxPower = 0;
        for (LabeledRedstoneLinkBlockEntity be : group) {
            if (!be.isReceiver()) {
                maxPower = Math.max(maxPower, be.getTransmittedSignal());
            }
        }

        // 通知所有接收端（对应原版的 IntConsumer callback）
        for (LabeledRedstoneLinkBlockEntity be : group) {
            if (be.isReceiver()) {
                be.onReceivedSignal(maxPower);
            }
        }
    }

    public void onFrequencyChanged(LabeledRedstoneLinkBlockEntity be, String oldFreq) {
        // 从旧网络移除并更新
        Set<LabeledRedstoneLinkBlockEntity> oldGroup = networks.get(oldFreq);
        if (oldGroup != null) {
            oldGroup.remove(be);
            if (oldGroup.isEmpty()) networks.remove(oldFreq);
            updateAll(oldFreq);
        }
        // 加入新网络并更新
        addToNetwork(be);
        updateAll(be.getFrequencyText());
    }
}