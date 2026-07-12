package com.molox.createimp.ponder.scenes;

import com.molox.createimp.registry.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ProcessManagerScenes {

    public static void monitorAndInterrupt(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("process_manager_monitor", "Monitoring and Interrupting Template Requests with the Process Manager");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // ---- D: the Template Panel wall, revealed in three quick steps ----
        BlockPos aPos = util.grid().at(5, 3, 1);
        BlockPos bPos = util.grid().at(5, 3, 2);
        BlockPos cPos = util.grid().at(5, 3, 3);
        BlockPos dPos = util.grid().at(5, 2, 1);
        BlockPos ePos = util.grid().at(5, 2, 2);

        Selection wallScaffolding = util.select().fromTo(6, 1, 1, 6, 1, 3);
        Selection wallCardboard = util.select().fromTo(6, 2, 1, 6, 3, 3);
        Selection wallGauges = util.select().fromTo(5, 2, 1, 5, 3, 3);

        scene.world().showSection(wallScaffolding, Direction.WEST);
        scene.idle(5);
        scene.world().showSection(wallCardboard, Direction.WEST);
        scene.idle(10);
        scene.world().showSection(wallGauges, Direction.EAST);
        scene.idle(25);

        // ---- The Vault and its front row enter together (the Process Manager's slot stays empty for now) ----
        BlockPos vaultPackagerPos = util.grid().at(3, 1, 4);
        BlockPos vaultLinkPos = util.grid().at(2, 1, 4);
        BlockPos processManagerPos = util.grid().at(1, 1, 4);

        Selection vaultAndRow = util.select().fromTo(1, 1, 4, 5, 2, 6)
                .substract(util.select().position(processManagerPos));
        scene.world().showSection(vaultAndRow, Direction.DOWN);
        scene.idle(30);

        // ---- The Work Warehouse and its own Packager enter, standalone as before ----
        Selection workWarehouseSection = util.select().position(util.grid().at(3, 1, 1));
        Selection workWarehousePackagerSection = util.select().position(util.grid().at(2, 1, 1));

        scene.world().showSection(workWarehouseSection, Direction.SOUTH);
        scene.idle(20);
        scene.world().showSection(workWarehousePackagerSection, Direction.SOUTH);
        scene.idle(40);

        // ---- Teach connecting the Process Manager to the logistics network, same lesson as before ----
        ItemStack processManagerItem = ModItems.PROCESS_MANAGER.toStack();
        scene.overlay().showControls(util.vector().topOf(vaultLinkPos), Pointing.DOWN, 50)
                .rightClick().withItem(processManagerItem);
        scene.idle(5);

        Vec3 linkIconPos = wallMountedIconPos(vaultLinkPos, Direction.EAST);
        AABB tuneBounds = new AABB(linkIconPos, linkIconPos).inflate(0.05, 0.28, 0.28);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, vaultLinkPos, tuneBounds, 10);
        scene.idle(1);

        tuneBounds = new AABB(linkIconPos, linkIconPos).inflate(0.05, 0.22, 0.22);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, vaultLinkPos, tuneBounds, 50);
        scene.idle(26);

        scene.overlay().showText(100)
                .text("Right-click the network's Stock Link to tune a Process Manager before placing it")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(linkIconPos);
        scene.idle(110);

        // ---- The Process Manager enters ----
        scene.overlay().showControls(util.vector().topOf(processManagerPos), Pointing.DOWN, 40)
                .rightClick().withItem(processManagerItem);
        scene.idle(10);

        Selection processManagerSection = util.select().position(processManagerPos);
        scene.world().showSection(processManagerSection, Direction.WEST);
        scene.idle(30);

        // ---- What the Process Manager does ----
        scene.overlay().showText(110)
                .text("The Process Manager can monitor Template requests currently in progress across the network")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(processManagerPos));
        scene.idle(120);

        scene.overlay().showText(110)
                .text("It also keeps a history log whenever a Template request completes or is interrupted")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(processManagerPos));
        scene.idle(120);

        scene.overlay().showText(120)
                .text("Opening the detail log of an ongoing request lets you interrupt that request")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(processManagerPos));
        scene.idle(130);

        scene.markAsFinished();
    }



    private static Vec3 wallMountedIconPos(BlockPos pos, Direction supportDirection) {
        Vec3 center = Vec3.atCenterOf(pos);
        double inset = 0.4;
        return center.add(
                supportDirection.getStepX() * inset,
                0.0,
                supportDirection.getStepZ() * inset
        );
    }
}