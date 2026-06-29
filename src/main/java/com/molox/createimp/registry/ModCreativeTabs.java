package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateImp.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATEIMP_TAB =
            CREATIVE_MODE_TABS.register("createimp_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createimp"))
                    .icon(() -> ModItems.NETWORK_MANAGER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.ANDESITE_SCRAP_BUCKET.get());
                        output.accept(ModItems.BRASS_SCRAP_BUCKET.get());
                        output.accept(ModItems.NETWORK_MANAGER.get());
                        output.accept(ModItems.LABELED_REDSTONE_LINK.get());
                        output.accept(ModItems.BATCH_MECHANICAL_CRAFTER.get());
                        output.accept(ModItems.BATCH_REPACKAGER.get());
                    })
                    .build());
}