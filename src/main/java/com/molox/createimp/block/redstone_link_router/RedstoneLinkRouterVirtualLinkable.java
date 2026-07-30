package com.molox.createimp.block.redstone_link_router;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;

/**
 * 路由器模块激活后，"就相当于用红石激活一个相同频率的无线红石终端"——这个类就是
 * 那个被虚拟出来的发送端，真实注册进 {@link RedstoneLinkNetworkHandler}
 * （{@code Create.REDSTONE_LINK_NETWORK_HANDLER}）里，让世界里其它调到同一物品频率
 * 的真实无线红石信号终端也能收到信号。只发送不接收，{@link #setReceivedStrength}
 * 是空实现；位置固定用路由器方块自己的坐标，和原版一样受传输距离限制
 * （{@code AllConfigs.server().logistics.linkRange}）。
 */
public class RedstoneLinkRouterVirtualLinkable implements IRedstoneLinkable {

    private final Couple<RedstoneLinkNetworkHandler.Frequency> networkKey;
    private final BlockPos location;
    private volatile int strength;
    private volatile boolean alive = true;

    public RedstoneLinkRouterVirtualLinkable(Couple<RedstoneLinkNetworkHandler.Frequency> networkKey,
                                             BlockPos location, int strength) {
        this.networkKey = networkKey;
        this.location = location;
        this.strength = strength;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }

    public void markRemoved() {
        this.alive = false;
    }

    @Override
    public int getTransmittedStrength() {
        return strength;
    }

    @Override
    public void setReceivedStrength(int strength) {
        // 只发送，不接收
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
        return networkKey;
    }

    @Override
    public BlockPos getLocation() {
        return location;
    }
}