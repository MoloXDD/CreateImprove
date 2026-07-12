package com.molox.createimp.ponder.scenes;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class WorkWarehouseConnectionScenes {

    public static void connectToVault(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("work_warehouse_connection", "Connecting a Work Warehouse to a Storage Block");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // ---- D: the Template Panel wall, with its gauges entering together ----
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

        // ---- The whole Vault cluster enters together: Vault + its front row
        //      (Stock Ticker, Blaze Burner, Packager, Stock Link, and the Work Warehouse
        //      itself in connection mode, filling the row's open slot) ----
        Selection vaultCluster = util.select().fromTo(1, 1, 4, 5, 2, 6);
        scene.world().showSection(vaultCluster, Direction.DOWN);
        scene.idle(30);

        BlockPos workWarehousePos = util.grid().at(1, 1, 4);
        BlockPos vaultPackagerPos = util.grid().at(3, 1, 4);
        BlockPos blazePos = util.grid().at(4, 1, 4);
        Vec3 vaultCenter = util.vector().of(3.0, 2.0, 5.5);

        scene.overlay().showText(100)
                .text("A Storage Block connected to the logistics network can be placed flush against the Vault")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(workWarehousePos));
        scene.idle(110);

        scene.overlay().showText(110)
                .text("This way, the Work Warehouse can retrieve items directly from the connected storage, without going through a logistics request")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vaultCenter);
        scene.idle(120);

        scene.overlay().showText(100)
                .text("The Work Warehouse's shipments will also be sent out directly through the connected storage's Packager")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(vaultPackagerPos));
        scene.idle(110);

        scene.overlay().showText(100)
                .text("The Work Warehouse's address should also be set to that connected storage's address")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(workWarehousePos));
        scene.idle(110);

        scene.overlay().showText(120)
                .text("When making a Template request, this is done by setting the address to the connected storage's routing address (changeable in the config, default is /back)")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(blazePos));
        scene.idle(130);

        scene.overlay().showText(110)
                .text("After finishing production, the Work Warehouse will send the product directly back to the connected storage")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vaultCenter);
        scene.idle(120);

        scene.markAsFinished();
    }


}