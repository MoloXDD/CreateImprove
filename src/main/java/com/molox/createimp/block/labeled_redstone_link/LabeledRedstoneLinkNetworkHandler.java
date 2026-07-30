package com.molox.createimp.block.labeled_redstone_link;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

public class LabeledRedstoneLinkNetworkHandler {

    private static final Map<ServerLevel, LabeledRedstoneLinkNetworkHandler> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    // 按频率分组的所有终端（发送端+接收端都在里面，真实方块实体和路由器虚拟发送端都算）
    private final Map<String, Set<LabeledRedstoneLinkable>> networks = new HashMap<>();

    private LabeledRedstoneLinkNetworkHandler() {}

    public static LabeledRedstoneLinkNetworkHandler get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return INSTANCES.computeIfAbsent(serverLevel, k -> new LabeledRedstoneLinkNetworkHandler());
        }
        return null;
    }

    public void addToNetwork(LabeledRedstoneLinkable be) {
        String freq = be.getFrequencyText();
        networks.computeIfAbsent(freq, k -> new HashSet<>()).add(be);
    }

    public void removeFromNetwork(LabeledRedstoneLinkable be) {
        String freq = be.getFrequencyText();
        Set<LabeledRedstoneLinkable> group = networks.get(freq);
        if (group != null) {
            group.remove(be);
            if (group.isEmpty()) networks.remove(freq);
        }
    }

    /**
     * 某发送端信号变化后，更新同频率所有接收端。
     * 完全对应原版 RedstoneLinkNetworkHandler.updateNetworkOf
     */
    public void updateNetworkOf(LabeledRedstoneLinkable transmitter) {
        String freq = transmitter.getFrequencyText();
        updateAll(freq);
    }

    public void updateAll(String frequency) {
        Set<LabeledRedstoneLinkable> group = networks.get(frequency);
        if (group == null || group.isEmpty()) return;

        // 取所有发送端的最大功率
        int maxPower = 0;
        for (LabeledRedstoneLinkable be : group) {
            if (!be.isReceiver()) {
                maxPower = Math.max(maxPower, be.getTransmittedSignal());
            }
        }

        // 通知所有接收端（对应原版的 IntConsumer callback）
        for (LabeledRedstoneLinkable be : group) {
            if (be.isReceiver()) {
                be.onReceivedSignal(maxPower);
            }
        }
    }

    public void onFrequencyChanged(LabeledRedstoneLinkable be, String oldFreq) {
        // 从旧网络移除并更新
        Set<LabeledRedstoneLinkable> oldGroup = networks.get(oldFreq);
        if (oldGroup != null) {
            oldGroup.remove(be);
            if (oldGroup.isEmpty()) networks.remove(oldFreq);
            updateAll(oldFreq);
        }
        // 加入新网络并更新
        addToNetwork(be);
        updateAll(be.getFrequencyText());
    }

    /**
     * 供路由器文本终端查询"这个频率当前有没有被点亮"：只要同频率组里存在至少一个
     * 发送端且发送功率大于 0，就视为这个频率被激活。完全对应原版
     * {@code RedstoneLinkNetworkHandler.hasAnyLoadedPower} 的语义。
     */
    public boolean hasAnyLoadedPower(String frequency) {
        Set<LabeledRedstoneLinkable> group = networks.get(frequency);
        if (group == null) return false;
        for (LabeledRedstoneLinkable be : group) {
            if (!be.isReceiver() && be.getTransmittedSignal() > 0) return true;
        }
        return false;
    }

    /**
     * 供路由器文本终端读取"这个频率当前的实际强度"（0-15，取所有发送端里的最大值），
     * 不只是布尔值——因为路由器需要按实际强度继续往下传播/参与与门或门的最大值计算。
     */
    public int getMaxTransmittedStrength(String frequency) {
        Set<LabeledRedstoneLinkable> group = networks.get(frequency);
        if (group == null) return 0;
        int maxPower = 0;
        for (LabeledRedstoneLinkable be : group) {
            if (!be.isReceiver()) {
                maxPower = Math.max(maxPower, be.getTransmittedSignal());
            }
        }
        return maxPower;
    }
}