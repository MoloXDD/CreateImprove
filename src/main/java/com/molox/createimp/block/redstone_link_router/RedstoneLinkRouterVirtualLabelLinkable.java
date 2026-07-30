package com.molox.createimp.block.redstone_link_router;

import com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkable;

/**
 * 路由器模块激活后，"就相当于用红石激活一个相同频率的无线红石终端"——这个类就是
 * 那个被虚拟出来的发送端，真实注册进 {@link com.molox.createimp.block.labeled_redstone_link.LabeledRedstoneLinkNetworkHandler}
 * 里，让同频率的真实标码终端也能收到信号。只发送不接收，{@link #onReceivedSignal}
 * 是空实现。
 */
public class RedstoneLinkRouterVirtualLabelLinkable implements LabeledRedstoneLinkable {

    private final String frequencyText;
    private volatile int strength;

    public RedstoneLinkRouterVirtualLabelLinkable(String frequencyText, int strength) {
        this.frequencyText = frequencyText;
        this.strength = strength;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }

    @Override
    public boolean isReceiver() {
        return false;
    }

    @Override
    public int getTransmittedSignal() {
        return strength;
    }

    @Override
    public void onReceivedSignal(int power) {
        // 只发送，不接收
    }

    @Override
    public String getFrequencyText() {
        return frequencyText;
    }
}