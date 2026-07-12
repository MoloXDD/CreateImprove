package com.molox.createimp.ponder;

import com.molox.createimp.ponder.scenes.TemplateChainScenes;
import com.molox.createimp.ponder.scenes.WorkWarehouseAutomationScenes;
import com.molox.createimp.ponder.scenes.WorkWarehouseConnectionScenes;
import com.molox.createimp.ponder.scenes.ProcessManagerScenes;
import com.molox.createimp.registry.ModItems;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public class AllCreateImpPonderScenes {

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<DeferredHolder<?, ?>> HELPER =
                helper.withKeyFunction(DeferredHolder::getId);

        HELPER.forComponents(ModItems.TEMPLATE_PANEL, ModItems.WORK_WAREHOUSE, ModItems.PROCESS_MANAGER)
                .addStoryBoard("template_panel/build_chain", TemplateChainScenes::buildChain)
                .addStoryBoard("template_panel/work_warehouse_automation", WorkWarehouseAutomationScenes::automateProduction)
                .addStoryBoard("template_panel/work_warehouse_connection", WorkWarehouseConnectionScenes::connectToVault)
                .addStoryBoard("template_panel/process_manager_monitor", ProcessManagerScenes::monitorAndInterrupt);
    }
}