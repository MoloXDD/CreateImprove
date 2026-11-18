package com.molox.createimp.compat;

import com.molox.createimp.CreateImp;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.util.PackagerSignAddressHelper;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scoped server-side context for Mobile Packages requests. No optional mod types occur here.
 * CFA's generic request allocator does not group links sharing an inventory, unlike Create's
 * allocator. Only inside a Mobile Packages request, apply Create Improve's existing sign
 * preference to those groups without affecting other users of CFA or stock queries.
 */
public final class MobilePackagesRequestContext implements AutoCloseable {
    private record Request(UUID network, String address) {}

    private static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();
    private final Request previous;

    private MobilePackagesRequestContext(UUID network, String address) {
        previous = CURRENT.get();
        CURRENT.set(new Request(network, address));
    }

    public static MobilePackagesRequestContext enter(UUID network, String address) {
        return new MobilePackagesRequestContext(network, address);
    }

    @Override
    public void close() {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    public static Collection<LogisticallyLinkedBehaviour> selectGenericLinks(
            UUID network, Collection<LogisticallyLinkedBehaviour> original) {
        Request request = CURRENT.get();
        if (request == null || !Objects.equals(network, request.network()) || original.size() < 2) {
            return original;
        }

        Map<InventoryIdentifier, List<LogisticallyLinkedBehaviour>> groups = new LinkedHashMap<>();
        for (LogisticallyLinkedBehaviour link : original) {
            InventoryIdentifier identifier = inventoryIdentifier(link);
            if (identifier != null) {
                groups.computeIfAbsent(identifier, ignored -> new ArrayList<>()).add(link);
            }
        }

        // Preserve CFA's priority order and all non-packager links. Do not change its behavior
        // when address filtering is disabled, including for a mixed item/fluid packager group.
        Map<InventoryIdentifier, LogisticallyLinkedBehaviour> selections = new LinkedHashMap<>();
        for (Map.Entry<InventoryIdentifier, List<LogisticallyLinkedBehaviour>> group : groups.entrySet()) {
            List<LogisticallyLinkedBehaviour> links = group.getValue();
            if (links.size() < 2 || links.stream().noneMatch(MobilePackagesRequestContext::filterEnabled)) {
                continue;
            }
            List<LogisticallyLinkedBehaviour> matched = new ArrayList<>();
            List<LogisticallyLinkedBehaviour> noSign = new ArrayList<>();
            for (LogisticallyLinkedBehaviour link : links) {
                if (!filterEnabled(link)) {
                    continue;
                }
                PackagerBlockEntity packager = ((PackagerLinkBlockEntity) link.blockEntity).getPackager();
                String sign = PackagerSignAddressHelper.resolveSignAddress(packager);
                if (sign == null) {
                    noSign.add(link);
                } else if (PackageItem.matchAddress(sign, request.address())) {
                    matched.add(link);
                }
            }
            // Same semantics as MixinLogisticsManager: matched > unsigned > original pool.
            // A sign is a preference between packagers sharing stock, not a network-wide veto.
            List<LogisticallyLinkedBehaviour> candidates = !matched.isEmpty() ? matched
                    : !noSign.isEmpty() ? noSign : links;
            selections.put(group.getKey(), candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
        }
        if (selections.isEmpty()) {
            return original;
        }
        List<LogisticallyLinkedBehaviour> result = new ArrayList<>();
        for (LogisticallyLinkedBehaviour link : original) {
            InventoryIdentifier identifier = inventoryIdentifier(link);
            LogisticallyLinkedBehaviour selected = identifier == null ? null : selections.get(identifier);
            if (selected == null || selected == link) {
                result.add(link);
            }
        }
        return result;
    }

    private static InventoryIdentifier inventoryIdentifier(LogisticallyLinkedBehaviour link) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe)) {
            return null;
        }
        PackagerBlockEntity packager = plbe.getPackager();
        if (packager == null || !packager.targetInventory.hasInventory()) {
            return null;
        }
        IdentifiedInventory inventory = packager.targetInventory.getIdentifiedInventory();
        return inventory == null ? null : inventory.identifier();
    }

    private static boolean filterEnabled(LogisticallyLinkedBehaviour link) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe)) {
            return false;
        }
        PackagerBlockEntity packager = plbe.getPackager();
        if (packager == null) {
            return false;
        }
        if (FluidLogisticsCompat.isLoaded() && TemplateFluidDisplayHelper.isFluidPackager(packager)) {
            return CreateImp.getConfig().modCompatConfig.fluidLogisticsCompat.fluidPackagerAddressFilterEnabled;
        }
        return CreateImp.getConfig().functionConfig.featureToggles.packagerAddressFilterEnabled;
    }
}
