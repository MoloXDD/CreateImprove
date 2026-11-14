package com.molox.createimp.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Gates optional integrations during Mixin discovery without loading their classes. */
public final class CreateImpMixinConfigPlugin implements IMixinConfigPlugin {
    private static final String SIMULATED_MOD_ID = "simulated";
    private boolean simulatedLoaded;
    private boolean mobilePackagesLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        simulatedLoaded = LoadingModList.get().getModFileById(SIMULATED_MOD_ID) != null;
        mobilePackagesLoaded = LoadingModList.get().getModFileById("create_mobile_packages") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (simpleName.startsWith("MixinMobilePackages")) return mobilePackagesLoaded;
        return simulatedLoaded || !simpleName.startsWith("MixinSimulated");
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
