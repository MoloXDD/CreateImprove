package com.molox.createimp.block.labeled_redstone_link;

import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class LabeledRedstoneLinkBlockEntity extends SmartBlockEntity
        implements ClipboardCloneable {

    public static final String DEFAULT_FREQUENCY = "默认红石频率";
    private static final String CLIPBOARD_KEY = "labeled_redstone_link";

    private String frequencyText = DEFAULT_FREQUENCY;

    // 对应原版的 transmittedSignal / receivedSignal / receivedSignalChanged
    private int transmittedSignal = 0;
    private int receivedSignal = 0;
    private boolean receivedSignalChanged = false;

    public LabeledRedstoneLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List behaviours) {
        // 不使用 LinkBehaviour，ClipboardCloneable 由实体本身实现
    }

    // ========== 频率 ==========

    public String getFrequencyText() {
        return frequencyText;
    }

    public void setFrequencyText(String text) {
        String newFreq = (text == null || text.isBlank()) ? DEFAULT_FREQUENCY : text;
        if (newFreq.equals(frequencyText)) return;

        if (level != null && !level.isClientSide()) {
            LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
            if (handler != null) {
                String old = frequencyText;
                this.frequencyText = newFreq;
                handler.onFrequencyChanged(this, old);
            } else {
                this.frequencyText = newFreq;
            }
            sendData();
        } else {
            this.frequencyText = newFreq;
        }
        setChanged();
    }

    // ========== 发送/接收模式 ==========

    public boolean isReceiver() {
        BlockState state = getBlockState();
        return state.hasProperty(LabeledRedstoneLinkBlock.RECEIVER)
                && state.getValue(LabeledRedstoneLinkBlock.RECEIVER);
    }

    // ========== 发送端信号（对应原版 transmit / getSignal）==========

    /** 发送端：当邻居变化时，Block调用此方法更新本地功率并通知网络 */
    public void transmit(int power) {
        this.transmittedSignal = power;
        LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
        if (handler != null) handler.updateNetworkOf(this);
    }

    public int getTransmittedSignal() {
        return transmittedSignal;
    }

    // ========== 接收端信号（对应原版 setSignal / getReceivedSignal）==========

    /** 由网络Handler调用，对应原版 IntConsumer callback */
    public void onReceivedSignal(int power) {
        if (receivedSignal == power) return;
        receivedSignalChanged = true;
        receivedSignal = power;
    }

    public int getReceivedSignal() {
        return receivedSignal;
    }

    // ========== tick：完全对应原版 BE.tick() 的接收端逻辑 ==========

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;

        BlockState state = getBlockState();

        if (!isReceiver()) {
            // 发送端：每 tick 主动重新检测本地信号并同步到网络
            // 这使发送端能即时响应模拟拉杆等信号源，不需要等待 neighborChanged
            LabeledRedstoneLinkBlock block = (LabeledRedstoneLinkBlock) state.getBlock();
            block.updateTransmittedSignal(state, level, worldPosition);
            return;
        }

        // 接收端：检测 receivedSignal 是否和 POWERED 状态一致
        boolean shouldBePowered = receivedSignal > 0;
        boolean currentlyPowered = state.getValue(LabeledRedstoneLinkBlock.POWERED);

        if (shouldBePowered != currentlyPowered) {
            receivedSignalChanged = true;
            level.setBlockAndUpdate(worldPosition,
                    state.setValue(LabeledRedstoneLinkBlock.POWERED, shouldBePowered));
        }

        if (receivedSignalChanged) {
            updateSelfAndAttached(getBlockState());
        }
    }

    /**
     * 对应原版 updateSelfAndAttached：通知自己和附着方块更新红石。
     * 原版通知了自己 pos 和附着方向 pos.relative(FACING.opposite)
     */
    private void updateSelfAndAttached(BlockState state) {
        if (level == null) return;
        Direction facing = state.getValue(LabeledRedstoneLinkBlock.FACING);
        BlockPos attachedPos = worldPosition.relative(facing.getOpposite());

        level.blockUpdated(worldPosition, state.getBlock());
        level.blockUpdated(attachedPos, level.getBlockState(attachedPos).getBlock());

        receivedSignalChanged = false;
    }

    // ========== 网络注册 ==========

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide()) {
            LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
            if (handler != null) handler.addToNetwork(this);
        }
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide()) {
            LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
            if (handler != null) {
                handler.removeFromNetwork(this);
                handler.updateAll(frequencyText);
            }
        }
        super.remove();
    }

    // ========== NBT ==========

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("FrequencyText", frequencyText);
        tag.putInt("TransmittedSignal", transmittedSignal);
        tag.putInt("ReceivedSignal", receivedSignal);
        tag.putBoolean("ReceivedSignalChanged", receivedSignalChanged);
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        frequencyText = tag.getString("FrequencyText");
        if (frequencyText.isBlank()) frequencyText = DEFAULT_FREQUENCY;
        transmittedSignal = tag.getInt("TransmittedSignal");
        receivedSignal = tag.getInt("ReceivedSignal");
        receivedSignalChanged = tag.getBoolean("ReceivedSignalChanged");
    }

    // ========== ClipboardCloneable ==========

    @Override
    public String getClipboardKey() {
        return CLIPBOARD_KEY;
    }

    @Override
    public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        tag.putString("Frequency", frequencyText);
        return true;
    }

    @Override
    public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag,
                                     Player player, Direction side, boolean simulate) {
        if (!tag.contains("Frequency")) return false;
        if (simulate) return true;
        setFrequencyText(tag.getString("Frequency"));
        return true;
    }
}