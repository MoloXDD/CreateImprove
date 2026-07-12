package com.molox.createimp.ponder;

import com.molox.createimp.CreateImp;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CreateImpPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return CreateImp.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        AllCreateImpPonderScenes.register(helper);
    }
}