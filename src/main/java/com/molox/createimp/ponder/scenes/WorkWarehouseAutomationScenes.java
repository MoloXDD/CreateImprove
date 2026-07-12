package com.molox.createimp.ponder.scenes;

import com.molox.createimp.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
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

public class WorkWarehouseAutomationScenes {

    public static void automateProduction(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("work_warehouse_automation", "Automating Template Production with the Work Warehouse");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // ---- D: the Template Panel wall, revealed in three quick steps like the first scene ----
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

        // ---- The whole Vault cluster (Vault + Blaze Burner + Stock Ticker + its Packager + Stock Link) enters together ----
        BlockPos vaultPackagerPos = util.grid().at(3, 1, 4);
        BlockPos vaultLinkPos = util.grid().at(2, 1, 4);

        Selection vaultCluster = util.select().fromTo(1, 1, 4, 5, 2, 6);
        scene.world().showSection(vaultCluster, Direction.DOWN);
        scene.idle(30);

        // ---- Teach connecting the Work Warehouse to the logistics network, same lesson as the Stock Link intro ----
        ItemStack workWarehouseItem = ModItems.WORK_WAREHOUSE.toStack();
        scene.overlay().showControls(util.vector().topOf(vaultLinkPos), Pointing.DOWN, 50)
                .rightClick().withItem(workWarehouseItem);
        scene.idle(5);

        Vec3 linkIconPos = wallMountedIconPos(vaultLinkPos, Direction.EAST);
        AABB tuneBounds = new AABB(linkIconPos, linkIconPos).inflate(0.05, 0.28, 0.28);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, vaultLinkPos, tuneBounds, 10);
        scene.idle(1);

        tuneBounds = new AABB(linkIconPos, linkIconPos).inflate(0.05, 0.22, 0.22);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, vaultLinkPos, tuneBounds, 50);
        scene.idle(26);

        scene.overlay().showText(100)
                .text("Right-click the network's Stock Link to tune a Work Warehouse before placing it")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(linkIconPos);
        scene.idle(110);

        // ---- The Work Warehouse enters ----
        BlockPos workWarehousePos = util.grid().at(3, 1, 1);
        Selection workWarehouseSection = util.select().position(workWarehousePos);

        scene.overlay().showControls(util.vector().topOf(workWarehousePos), Pointing.DOWN, 50)
                .rightClick().withItem(workWarehouseItem);
        scene.idle(40);

        scene.world().showSection(workWarehouseSection, Direction.SOUTH);
        scene.idle(30);

        // ---- Teach configuring the Work Warehouse's address ----
        scene.overlay().showControls(util.vector().topOf(workWarehousePos), Pointing.DOWN, 50)
                .rightClick();
        scene.idle(5);

        scene.overlay().showText(100)
                .text("Right-click a Work Warehouse to open its interface and configure its address")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().topOf(workWarehousePos));
        scene.idle(110);

        // ---- The Work Warehouse's own Packager enters ----
        BlockPos workWarehousePackagerPos = util.grid().at(2, 1, 1);
        Selection workWarehousePackagerSection = util.select().position(workWarehousePackagerPos);
        scene.world().showSection(workWarehousePackagerSection, Direction.SOUTH);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("Attach a Packager to the Work Warehouse")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(workWarehousePackagerPos));
        scene.idle(100);

        // ---- Placing a Template request at the Stock Keeper (the hired Blaze standing by the Blaze Burner) ----
        BlockPos blazePos = util.grid().at(4, 1, 4);
        scene.overlay().showControls(util.vector().topOf(blazePos), Pointing.DOWN, 50)
                .rightClick();
        scene.idle(5);

        scene.overlay().showText(100)
                .text("Place a Template production request at the Stock Keeper")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(blazePos));
        scene.idle(110);

        scene.overlay().showText(100)
                .text("The number of Work Warehouses in the network determines how many Template requests can be handled at once")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(workWarehousePos));
        scene.idle(110);

        // ---- The vault's Packager takes a package out to fulfill the request ----
        ItemStack box = PackageStyles.getDefaultBox().copy();
        PackageItem.addAddress(box, "warehouse");
        scene.world().modifyBlockEntity(vaultPackagerPos, PackagerBlockEntity.class, be -> {
            be.animationTicks = 20;
            be.animationInward = false;
            be.heldBox = box;
        });
        scene.idle(20);

        scene.overlay().showText(110)
                .text("The Work Warehouse will request materials from the logistics network to its address")
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(vaultPackagerPos));
        scene.idle(120);

        // ---- Take the package from the vault's Packager (it empties out) and deliver it into the Work Warehouse's Packager ----
        scene.overlay().showControls(util.vector().topOf(vaultPackagerPos), Pointing.DOWN, 40)
                .rightClick();
        scene.idle(10);

        scene.world().modifyBlockEntity(vaultPackagerPos, PackagerBlockEntity.class,
                be -> be.heldBox = ItemStack.EMPTY);
        scene.idle(10);

        scene.overlay().showControls(util.vector().topOf(workWarehousePackagerPos), Pointing.DOWN, 40)
                .rightClick().withItem(box);
        scene.idle(20);

        scene.world().modifyBlockEntity(workWarehousePackagerPos, PackagerBlockEntity.class, be -> {
            be.animationTicks = 20;
            be.animationInward = true;
            be.previouslyUnwrapped = box;
        });
        scene.idle(30);

        // ---- The Work Warehouse's Packager sends the finished product back out ----
        ItemStack outgoingBox = PackageStyles.getDefaultBox().copy();
        PackageItem.addAddress(outgoingBox, "warehouse");
        scene.world().modifyBlockEntity(workWarehousePackagerPos, PackagerBlockEntity.class, be -> {
            be.animationTicks = 20;
            be.animationInward = false;
            be.heldBox = outgoingBox;
        });
        scene.idle(20);

        scene.overlay().showText(120)
                .text("The Packager connected to the Work Warehouse will send materials out to the address configured on a Template Panel to begin production")
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(workWarehousePackagerPos));
        scene.idle(130);

        // ---- The Work Warehouse's Packager sends that outgoing package away (it empties out) ----
        scene.overlay().showControls(util.vector().topOf(workWarehousePackagerPos), Pointing.DOWN, 40)
                .rightClick();
        scene.idle(10);

        scene.world().modifyBlockEntity(workWarehousePackagerPos, PackagerBlockEntity.class,
                be -> be.heldBox = ItemStack.EMPTY);
        scene.idle(15);

        // ---- Finished products need to return to the logistics network ----
        Vec3 vaultCenter = util.vector().of(3.0, 2.0, 5.5);
        scene.overlay().showText(110)
                .text("Products from finished production need to return to the logistics network")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(vaultCenter);
        scene.idle(120);

        // ---- The vault's Packager fetches the requested product from the network ----
        ItemStack productBox = PackageStyles.getDefaultBox().copy();
        PackageItem.addAddress(productBox, "warehouse");
        scene.world().modifyBlockEntity(vaultPackagerPos, PackagerBlockEntity.class, be -> {
            be.animationTicks = 20;
            be.animationInward = false;
            be.heldBox = productBox;
        });
        scene.idle(20);

        scene.overlay().showText(120)
                .text("When the Work Warehouse detects the matching product in the logistics network, it will request it to the Work Warehouse's address")
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(vaultPackagerPos));
        scene.idle(130);

        // ---- The cycle repeats: take the package, deliver it into the Work Warehouse ----
        scene.overlay().showControls(util.vector().topOf(vaultPackagerPos), Pointing.DOWN, 40)
                .rightClick();
        scene.idle(10);

        scene.world().modifyBlockEntity(vaultPackagerPos, PackagerBlockEntity.class,
                be -> be.heldBox = ItemStack.EMPTY);
        scene.idle(10);

        scene.overlay().showControls(util.vector().topOf(workWarehousePackagerPos), Pointing.DOWN, 40)
                .rightClick().withItem(productBox);
        scene.idle(20);

        scene.world().modifyBlockEntity(workWarehousePackagerPos, PackagerBlockEntity.class, be -> {
            be.animationTicks = 20;
            be.animationInward = true;
            be.previouslyUnwrapped = productBox;
        });
        scene.idle(20);

        scene.overlay().showText(120)
                .text("This process repeats until the requested Template product is fully produced")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(workWarehousePos));
        scene.idle(130);

        // ---- The Work Warehouse's Packager finally outputs the completed product ----
        ItemStack finalBox = PackageStyles.getDefaultBox().copy();
        PackageItem.addAddress(finalBox, "warehouse");
        scene.world().modifyBlockEntity(workWarehousePackagerPos, PackagerBlockEntity.class, be -> {
            be.animationTicks = 20;
            be.animationInward = false;
            be.heldBox = finalBox;
        });
        scene.idle(20);

        scene.overlay().showText(110)
                .text("The Work Warehouse will send the finished product to the address set when the request was made")
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(util.vector().topOf(workWarehousePackagerPos));
        scene.idle(120);

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