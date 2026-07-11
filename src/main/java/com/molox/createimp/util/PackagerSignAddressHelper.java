package com.molox.createimp.util;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

public class PackagerSignAddressHelper {

    private PackagerSignAddressHelper() {
    }

    /**
     * 返回值含义：
     * null  —— 六个方向都没有归属于这个打包机的告示牌
     * ""    —— 有归属于这个打包机的告示牌，但正反面文字都是空白
     * 其他  —— 归属于这个打包机的告示牌解析出的地址文字
     */
    public static String resolveSignAddress(PackagerBlockEntity packager) {
        Level level = packager.getLevel();
        if (level == null) {
            return null;
        }
        BlockPos packagerPos = packager.getBlockPos();
        boolean signFound = false;
        String result = "";
        for (Direction side : Iterate.directions) {
            BlockPos signPos = packagerPos.relative(side);
            BlockEntity blockEntity = level.getBlockEntity(signPos);
            if (!(blockEntity instanceof SignBlockEntity sign)) {
                continue;
            }
            if (!isAttachedTo(level.getBlockState(signPos), signPos, packagerPos)) {
                continue;
            }
            signFound = true;
            String text = getSignFaceText(sign);
            if (text != null) {
                result = text;
            }
        }
        return signFound ? result : null;
    }

    /**
     * 判断这块告示牌实际贴合的方块是不是 targetPos：
     * 墙上告示牌看 FACING 属性背对的那一侧（文字朝外，背面贴墙）；
     * 地面立牌固定贴合正下方那一格，跟 ROTATION 属性无关。
     */
    private static boolean isAttachedTo(BlockState signState, BlockPos signPos, BlockPos targetPos) {
        if (signState.getBlock() instanceof WallSignBlock) {
            Direction facing = signState.getValue(WallSignBlock.FACING);
            return signPos.relative(facing.getOpposite()).equals(targetPos);
        }
        if (signState.getBlock() instanceof StandingSignBlock) {
            return signPos.below().equals(targetPos);
        }
        return false;
    }

    private static String getSignFaceText(SignBlockEntity sign) {
        for (boolean front : Iterate.trueAndFalse) {
            SignText text = sign.getText(front);
            StringBuilder address = new StringBuilder();
            for (Component component : text.getMessages(false)) {
                String line = component.getString();
                if (line.isBlank()) {
                    continue;
                }
                address.append(line.trim()).append(' ');
            }
            if (!address.toString().isBlank()) {
                return address.toString().trim();
            }
        }
        return null;
    }
}