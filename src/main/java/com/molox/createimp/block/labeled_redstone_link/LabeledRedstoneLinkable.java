package com.molox.createimp.block.labeled_redstone_link;

/**
 * 标码无线红石信号终端网络（{@link LabeledRedstoneLinkNetworkHandler}）里的一个成员。
 * 真实方块实体（{@link LabeledRedstoneLinkBlockEntity}）和路由器用来"虚拟广播"的
 * 发送端（{@code RedstoneLinkRouterVirtualLabelLinkable}）都实现这个接口，这样网络
 * 处理器就不需要关心成员到底是不是一个真实方块。
 */
public interface LabeledRedstoneLinkable {

    boolean isReceiver();

    int getTransmittedSignal();

    void onReceivedSignal(int power);

    String getFrequencyText();
}