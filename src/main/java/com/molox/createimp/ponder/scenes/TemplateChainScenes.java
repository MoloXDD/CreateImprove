package com.molox.createimp.ponder.scenes;

import com.molox.createimp.block.template_panel.TemplatePanelBlock;
import com.molox.createimp.block.template_panel.TemplatePanelBlockEntity;
import com.molox.createimp.block.template_panel.TemplatePanelPosition;
import com.molox.createimp.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TemplateChainScenes {

    public static void buildChain(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("template_panel_build_chain", "Building a Template Chain with the Template Panel");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        Selection scaffoldingWall = util.select().fromTo(2, 1, 3, 4, 1, 3);
        Selection cardboardWall = util.select().fromTo(2, 2, 3, 4, 3, 3);
        BlockPos linkPos = util.grid().at(0, 1, 3);
        Selection linkS = util.select().position(linkPos);

        scene.world().showSection(scaffoldingWall, Direction.DOWN);
        scene.idle(5);

        scene.world().showSection(cardboardWall, Direction.DOWN);
        scene.idle(20);

        scene.world().showSection(linkS, Direction.DOWN);
        scene.idle(15);

        ItemStack templatePanelItem = ModItems.TEMPLATE_PANEL.toStack();
        scene.overlay().showControls(util.vector().topOf(linkPos), Pointing.DOWN, 50)
                .rightClick().withItem(templatePanelItem);
        scene.idle(5);

        AABB linkBounds = new AABB(linkPos);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, linkPos, linkBounds.deflate(0.45), 10);
        scene.idle(1);

        linkBounds = linkBounds.deflate(0.0625).contract(0.0, 0.5, 0.0);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, linkPos, linkBounds, 50);
        scene.idle(26);

        scene.overlay().showText(100)
                .text("Right-click a Stock Link before placement to connect it to the entire logistics network")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(util.vector().centerOf(linkPos));
        scene.idle(80);

        BlockPos aPos = util.grid().at(4, 3, 2);
        BlockPos bPos = util.grid().at(3, 3, 2);
        BlockPos cPos = util.grid().at(2, 3, 2);
        BlockPos dPos = util.grid().at(4, 2, 2);
        BlockPos ePos = util.grid().at(3, 2, 2);

        TemplatePanelPosition bPanelPos = new TemplatePanelPosition(bPos, TemplatePanelBlock.PanelSlot.BOTTOM_LEFT);
        TemplatePanelPosition cPanelPos = new TemplatePanelPosition(cPos, TemplatePanelBlock.PanelSlot.BOTTOM_LEFT);
        TemplatePanelPosition ePanelPos = new TemplatePanelPosition(ePos, TemplatePanelBlock.PanelSlot.BOTTOM_LEFT);
        TemplatePanelPosition aPanelPos = new TemplatePanelPosition(aPos, TemplatePanelBlock.PanelSlot.BOTTOM_LEFT);
        TemplatePanelPosition dPanelPos = new TemplatePanelPosition(dPos, TemplatePanelBlock.PanelSlot.BOTTOM_LEFT);

        ItemStack shieldStack = new ItemStack(Items.SHIELD);
        ItemStack oakPlanksStack = new ItemStack(Items.OAK_PLANKS);
        ItemStack oakLogStack = new ItemStack(Items.OAK_LOG);
        ItemStack ironIngotStack = new ItemStack(Items.IRON_INGOT);
        ItemStack ironBlockStack = new ItemStack(Items.IRON_BLOCK);
        ItemStack factoryGaugeItem = AllBlocks.FACTORY_GAUGE.asStack();

        // ---- C: template panel monitoring a Shield ----
        placeTemplatePanel(scene, util, cPos, templatePanelItem);
        monitorItemOnTemplatePanel(scene, cPos, shieldStack,
                "Right-click a Template Panel with the item that should be monitored");

        // ---- B: template panel monitoring Oak Planks ----
        placeTemplatePanel(scene, util, bPos, templatePanelItem);
        monitorItemOnTemplatePanel(scene, bPos, oakPlanksStack, null);

        // ---- Connect B -> C: Shields need Oak Planks (open C, then click B) ----
        beginConnection(scene, cPos,
                "From the target's panel menu, click Add New Link to start a connection");
        completeConnection(scene, cPos, bPos);
        scene.world().modifyBlockEntity(cPos, TemplatePanelBlockEntity.class,
                be -> be.panels.get(TemplatePanelBlock.PanelSlot.BOTTOM_LEFT).addConnection(bPanelPos));
        scene.idle(45);

        scene.overlay().showText(100)
                .text("Then click another panel to connect it as an upstream ingredient source")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(panelIconPos(cPos));
        scene.idle(90);

        // ---- A: ordinary Factory Gauge monitoring Oak Log ----
        placeFactoryGauge(scene, util, aPos, factoryGaugeItem);
        monitorItemOnFactoryGauge(scene, aPos, oakLogStack);

        // ---- Connect A -> B: Oak Planks need Oak Logs (open B, then click A) ----
        beginConnection(scene, bPos, null);
        completeConnection(scene, bPos, aPos);
        scene.world().modifyBlockEntity(bPos, TemplatePanelBlockEntity.class,
                be -> be.panels.get(TemplatePanelBlock.PanelSlot.BOTTOM_LEFT).addConnection(aPanelPos));
        scene.idle(45);

        // ---- E: template panel monitoring an Iron Ingot ----
        placeTemplatePanel(scene, util, ePos, templatePanelItem);
        monitorItemOnTemplatePanel(scene, ePos, ironIngotStack, null);

        // ---- D: ordinary Factory Gauge monitoring an Iron Block ----
        placeFactoryGauge(scene, util, dPos, factoryGaugeItem);
        monitorItemOnFactoryGauge(scene, dPos, ironBlockStack);

        // ---- Connect D -> E: Iron Ingots need Iron Blocks (open E, then click D) ----
        beginConnection(scene, ePos, null);
        completeConnection(scene, ePos, dPos);
        scene.world().modifyBlockEntity(ePos, TemplatePanelBlockEntity.class,
                be -> be.panels.get(TemplatePanelBlock.PanelSlot.BOTTOM_LEFT).addConnection(dPanelPos));
        scene.idle(45);

        // ---- Connect E -> C: Shields also need Iron Ingots (C's menu is already familiar, skip straight to +) ----
        scene.overlay().showControls(panelIconPos(cPos), Pointing.DOWN, 40)
                .showing((ScreenElement) AllIcons.I_ADD);
        scene.idle(50);
        completeConnection(scene, cPos, ePos);
        scene.world().modifyBlockEntity(cPos, TemplatePanelBlockEntity.class,
                be -> be.panels.get(TemplatePanelBlock.PanelSlot.BOTTOM_LEFT).addConnection(ePanelPos));
        scene.idle(60);

        // ---- Rule: leaf nodes must be ordinary Factory Gauges (highlight both A and D) ----
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, aPos, panelBox(aPos), 90);
        scene.idle(15);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, dPos, panelBox(dPos), 75);
        scene.idle(20);

        scene.overlay().showText(140)
                .text("A Template Chain can only end in an ordinary Factory Gauge, and only an ordinary Factory Gauge may connect into a Template Panel")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(panelIconPos(aPos));
        scene.idle(130);

        // ---- Configure an address on every Template Panel in the chain ----
        String demoAddress = "warehouse";
        scene.world().modifyBlockEntity(bPos, TemplatePanelBlockEntity.class,
                be -> be.panels.get(TemplatePanelBlock.PanelSlot.BOTTOM_LEFT).recipeAddress = demoAddress);
        scene.world().modifyBlockEntity(cPos, TemplatePanelBlockEntity.class,
                be -> be.panels.get(TemplatePanelBlock.PanelSlot.BOTTOM_LEFT).recipeAddress = demoAddress);
        scene.world().modifyBlockEntity(ePos, TemplatePanelBlockEntity.class,
                be -> be.panels.get(TemplatePanelBlock.PanelSlot.BOTTOM_LEFT).recipeAddress = demoAddress);

        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, bPos, panelBox(bPos), 60);
        scene.idle(15);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, cPos, panelBox(cPos), 60);
        scene.idle(15);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, ePos, panelBox(ePos), 60);
        scene.idle(20);

        scene.overlay().showText(140)
                .text("An address must be configured on every Template Panel for the Template Chain to be valid")
                .attachKeyFrame()
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .pointAt(panelIconPos(bPos));
        scene.idle(130);

        // ---- Highlight the entire completed chain ----
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, aPos, panelBox(aPos), 100);
        scene.idle(10);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, bPos, panelBox(bPos), 90);
        scene.idle(10);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, cPos, panelBox(cPos), 80);
        scene.idle(10);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, dPos, panelBox(dPos), 70);
        scene.idle(10);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, ePos, panelBox(ePos), 60);
        scene.idle(25);

        scene.overlay().showText(160)
                .text("This is a complete and valid Template Chain: every item monitored by a Template Panel in it can now be requested from the Stock Keeper menu")
                .attachKeyFrame()
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .pointAt(panelIconPos(bPos));
        scene.idle(160);

        scene.markAsFinished();
    }

    private static void placeTemplatePanel(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, ItemStack templatePanelItem) {
        Selection panelS = util.select().position(pos);
        scene.overlay().showControls(panelIconPos(pos), Pointing.DOWN, 50)
                .withItem(templatePanelItem).rightClick();
        scene.idle(7);
        scene.world().showSection(panelS, Direction.WEST);
        scene.world().modifyBlockEntity(pos, TemplatePanelBlockEntity.class,
                be -> be.addPanel(TemplatePanelBlock.PanelSlot.BOTTOM_LEFT, null));
        scene.idle(60);
    }

    private static void placeFactoryGauge(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, ItemStack factoryGaugeItem) {
        Selection panelS = util.select().position(pos);
        scene.overlay().showControls(panelIconPos(pos), Pointing.DOWN, 50)
                .withItem(factoryGaugeItem).rightClick();
        scene.idle(7);
        scene.world().showSection(panelS, Direction.WEST);
        scene.world().modifyBlockEntity(pos, FactoryPanelBlockEntity.class, be -> {
            FactoryPanelBehaviour behaviour = be.panels.get(FactoryPanelBlock.PanelSlot.BOTTOM_LEFT);
            behaviour.active = true;
        });
        scene.idle(60);
    }

    private static void monitorItemOnTemplatePanel(SceneBuilder scene, BlockPos pos, ItemStack monitorItem, String captionOrNull) {
        scene.overlay().showControls(panelIconPos(pos), Pointing.DOWN, 50)
                .withItem(monitorItem).rightClick();
        scene.idle(7);
        scene.world().modifyBlockEntity(pos, TemplatePanelBlockEntity.class,
                be -> be.panels.get(TemplatePanelBlock.PanelSlot.BOTTOM_LEFT).setFilter(monitorItem));
        if (captionOrNull != null) {
            scene.overlay().showText(80)
                    .text(captionOrNull)
                    .attachKeyFrame()
                    .placeNearTarget()
                    .pointAt(panelIconPos(pos));
        }
        scene.idle(90);
    }

    private static void monitorItemOnFactoryGauge(SceneBuilder scene, BlockPos pos, ItemStack monitorItem) {
        scene.overlay().showControls(panelIconPos(pos), Pointing.DOWN, 50)
                .withItem(monitorItem).rightClick();
        scene.idle(7);
        scene.world().modifyBlockEntity(pos, FactoryPanelBlockEntity.class,
                be -> be.panels.get(FactoryPanelBlock.PanelSlot.BOTTOM_LEFT).setFilter(monitorItem));
        scene.idle(60);
    }

    private static void beginConnection(SceneBuilder scene, BlockPos targetPos, String captionOrNull) {
        scene.overlay().showControls(panelIconPos(targetPos), Pointing.DOWN, 40).rightClick();
        scene.idle(7);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, targetPos, panelBox(targetPos), 100);
        if (captionOrNull != null) {
            scene.overlay().showText(70)
                    .text(captionOrNull)
                    .attachKeyFrame()
                    .placeNearTarget()
                    .pointAt(panelIconPos(targetPos));
        }
        scene.idle(40);
        scene.overlay().showControls(panelIconPos(targetPos), Pointing.DOWN, 40)
                .showing((ScreenElement) AllIcons.I_ADD);
        scene.idle(50);
    }

    private static void completeConnection(SceneBuilder scene, BlockPos targetPos, BlockPos sourcePos) {
        scene.overlay().showControls(panelIconPos(sourcePos), Pointing.DOWN, 50).rightClick();
        scene.idle(7);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, sourcePos, panelBox(sourcePos), 40);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, targetPos, panelBox(targetPos), 40);
        scene.idle(10);
    }

    /**
     * BOTTOM_LEFT 槽位在 FACE=WALL、FACING=NORTH 状态下的真实局部坐标，
     * 按 TemplatePanelBlockEntity.getShape() 里同样的旋转公式手工代入算出，
     * 结果是槽位贴在方块靠纸板墙那一侧的面上（局部坐标约 0.75, 0.25, 0.9375）。
     */
    private static Vec3 panelIconPos(BlockPos pos) {
        return new Vec3(pos.getX() + 0.75, pos.getY() + 0.25, pos.getZ() + 0.9375);
    }

    private static AABB panelBox(BlockPos pos) {
        Vec3 c = panelIconPos(pos);
        return new AABB(c, c).inflate(0.19, 0.19, 0.03);
    }

}