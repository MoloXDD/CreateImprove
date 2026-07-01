package com.molox.createimp.registry;

import com.molox.createimp.CreateImp;
import com.molox.createimp.screen.BrassScrapBucketMenu;
import com.molox.createimp.screen.NetworkManagerLabelEditMenu;
import com.molox.createimp.screen.NetworkManagerLabelEditorMenu;
import com.molox.createimp.screen.TemplatePanelSetItemMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, CreateImp.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<NetworkManagerLabelEditorMenu>> NETWORK_MANAGER_LABEL_EDITOR =
            MENU_TYPES.register("network_manager_label_editor",
                    () -> IMenuTypeExtension.create(NetworkManagerLabelEditorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<NetworkManagerLabelEditMenu>> NETWORK_MANAGER_LABEL_EDIT =
            MENU_TYPES.register("network_manager_label_edit",
                    () -> IMenuTypeExtension.create(NetworkManagerLabelEditMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<BrassScrapBucketMenu>> BRASS_SCRAP_BUCKET =
            MENU_TYPES.register("brass_scrap_bucket",
                    () -> IMenuTypeExtension.create(BrassScrapBucketMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<TemplatePanelSetItemMenu>> TEMPLATE_PANEL_SET_ITEM =
            MENU_TYPES.register("template_panel_set_item",
                    () -> IMenuTypeExtension.create(TemplatePanelSetItemMenu::new));
}