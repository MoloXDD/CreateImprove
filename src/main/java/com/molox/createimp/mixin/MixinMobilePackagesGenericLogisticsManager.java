package com.molox.createimp.mixin;

import com.molox.createimp.compat.MobilePackagesRequestContext;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.UUID;

/** Optional CFA integration, enabled only together with Mobile Packages. */
@Mixin(targets = "ru.zznty.create_factory_abstractions.generic.support.GenericLogisticsManager", remap = false)
public abstract class MixinMobilePackagesGenericLogisticsManager {
    @Redirect(method = "findPackagersForRequest", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagerLink/LogisticallyLinkedBehaviour;getAllPresent(Ljava/util/UUID;Z)Ljava/util/Collection;"))
    private static Collection<LogisticallyLinkedBehaviour> createimp$selectByRequestAddress(UUID network, boolean sortByPriority) {
        Collection<LogisticallyLinkedBehaviour> original = LogisticallyLinkedBehaviour.getAllPresent(network, sortByPriority);
        return MobilePackagesRequestContext.selectGenericLinks(network, original);
    }
}
