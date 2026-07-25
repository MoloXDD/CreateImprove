package com.molox.createimp.block.template_panel;

import com.molox.createimp.CreateImp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 【加固说明】这个类原来的实现直接遍历 {@code be.panels.values()} 和
 * {@code behaviour.targetedBy.values()} 这两个活的集合，一旦渲染过程中这两个
 * 集合被别的地方（网络包处理、方块实体自己的 tick）同时修改，就有
 * {@code ConcurrentModificationException} 的风险；而且原来的实现完全没有
 * 异常保护——{@code connection.getPath(...)}、{@code AllPartialModels...get(...)}
 * 拿到 null、或者 {@code CachedBuffers.partial(...)} 这些调用只要有一个抛出
 * 异常，就会中断整个渲染流程，让方块连接线和外观都消失不见。
 * <p>
 * 实机遇到过一次"模板仪表突发不渲染、重启/重载资源包才恢复"的问题，排查后
 * 高度怀疑是某个第三方渲染优化模组（给 Catnip 的 {@code SuperByteBuffer} 做了
 * 一层用静态共享原生内存缓冲区实现的加速，且这个缓冲区的写入过程没有做
 * 异常安全保护）在渲染中途抛异常时把这个全局共享状态搞乱，导致后续渲染
 * 持续失败直到重启。既然那个第三方实现本身不受我们控制，这里就把我们自己
 * 这一段加固：确保我们的渲染代码本身绝不会在中途抛出异常触发这个问题——
 * 每一条连接线单独用 try-catch 包起来，某一条出问题只跳过这一条（下一帧
 * 数据恢复正常后自动恢复正常绘制），不会波及同一个仪表的其他连接线，更
 * 不会波及其他仪表；并且改成先复制一份快照再遍历，避免并发修改异常。
 * 这些都只是保护层，不改变原有的绘制内容和判断逻辑，正常情况下画出来的
 * 东西和之前完全一样。
 */
public class TemplatePanelRenderer extends SmartBlockEntityRenderer<TemplatePanelBlockEntity> {

    private static long lastErrorLogTime = 0L;
    private static final long ERROR_LOG_INTERVAL_MS = 5000L;

    public TemplatePanelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(TemplatePanelBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        List<TemplatePanelBehaviour> behaviours = new ArrayList<>(be.panels.values());
        for (TemplatePanelBehaviour behaviour : behaviours) {
            if (behaviour == null || !behaviour.isActive()) {
                continue;
            }
            List<TemplatePanelConnection> connections = new ArrayList<>(behaviour.targetedBy.values());
            for (TemplatePanelConnection connection : connections) {
                if (connection == null) {
                    continue;
                }
                try {
                    renderPath(behaviour, connection, partialTicks, ms, buffer, light, overlay);
                } catch (Exception e) {
                    createimp$logRenderFailure(e);
                }
            }
        }
    }

    private static void createimp$logRenderFailure(Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLogTime < ERROR_LOG_INTERVAL_MS) {
            return;
        }
        lastErrorLogTime = now;
        CreateImp.LOGGER.error("模板仪表连接线渲染失败，已跳过这一条连接（这条日志最多每{}毫秒打印一次）", ERROR_LOG_INTERVAL_MS, e);
    }

    public static void renderPath(TemplatePanelBehaviour behaviour, TemplatePanelConnection connection, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (behaviour.getWorld() == null) {
            return;
        }
        BlockState blockState = behaviour.blockEntity.getBlockState();
        List<Direction> path = connection.getPath(behaviour.getWorld(), blockState, behaviour.getPanelPosition());
        if (path == null || path.isEmpty()) {
            return;
        }
        float xRot = TemplatePanelBlock.getXRot(blockState) + 1.5707964f;
        float yRot = TemplatePanelBlock.getYRot(blockState);
        int color = behaviour.getIngredientStatusColor();
        float yOffset = 1.0f;

        float currentX = 0.0f;
        float currentZ = 0.0f;
        for (int i = 0; i < path.size(); ++i) {
            Direction direction = path.get(i);
            if (direction == null) {
                continue;
            }
            currentX = (float) (currentX + direction.getStepX() * 0.5);
            currentZ = (float) (currentZ + direction.getStepZ() * 0.5);
            boolean isArrowSegment = i == 0;
            PartialModel partial = (isArrowSegment ? AllPartialModels.FACTORY_PANEL_ARROWS : AllPartialModels.FACTORY_PANEL_LINES)
                    .get(direction.getOpposite());
            if (partial == null) {
                continue;
            }
            SuperByteBuffer connectionSprite = CachedBuffers.partial(partial, blockState)
                    .rotateCentered(yRot, Direction.UP)
                    .rotateCentered(xRot, Direction.EAST)
                    .rotateCentered((float) Math.PI, Direction.UP)
                    .translate(behaviour.slot.xOffset * 0.5 + 0.25, 0.0, behaviour.slot.yOffset * 0.5 + 0.25)
                    .translate(currentX, (yOffset + (direction.get2DDataValue() % 2) * 0.125f) / 512.0f, currentZ);
            connectionSprite.color(color).light(light).overlay(overlay).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        }
    }
}