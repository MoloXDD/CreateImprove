package com.molox.createimp.mixin;

import com.molox.createimp.CreateImp;
import com.molox.createimp.network.SaveFactoryPanelDemandModePacket;
import com.molox.createimp.util.IFactoryPanelBehaviourDemandMode;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = FactoryPanelScreen.class, remap = false)
public abstract class MixinFactoryPanelScreen {

    @Unique
    private static final ResourceLocation DEMAND_MODE_ICON =
            ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "textures/gui/demand_request_button.png");

    @Unique
    private IconButton createimp$demandModeButton = null;

    @Shadow private FactoryPanelBehaviour behaviour;
    @Shadow private boolean restocker;
    @Shadow private List<FactoryPanelConnection> connections;

    @Unique
    private AbstractSimiScreenAccessor createimp$asAccessor() {
        return (AbstractSimiScreenAccessor) (Object) this;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void createimp$init(CallbackInfo ci) {
        createimp$demandModeButton = null;

        if (restocker) return;
        if (connections == null || connections.isEmpty()) return;

        ScreenElement icon = (graphics, x, y) ->
                graphics.blit(DEMAND_MODE_ICON, x, y, 0, 0, 16, 16, 16, 16);

        int x = createimp$asAccessor().createimp$getGuiLeft() + 159;
        int y = createimp$asAccessor().createimp$getGuiTop() + 67;

        createimp$demandModeButton = new IconButton(x, y, icon);

        createimp$demandModeButton.green =
                ((IFactoryPanelBehaviourDemandMode) behaviour).createimp$isDemandMode();

        createimp$demandModeButton.withCallback(() -> {
            boolean newState = !((IFactoryPanelBehaviourDemandMode) behaviour).createimp$isDemandMode();
            ((IFactoryPanelBehaviourDemandMode) behaviour).createimp$setDemandMode(newState);
            createimp$demandModeButton.green = newState;
            CatnipServices.NETWORK.sendToServer(
                    new SaveFactoryPanelDemandModePacket(behaviour.getPanelPosition(), newState));
        });

        createimp$demandModeButton.setToolTip(
                Component.translatable("createimp.gui.factory_panel.demand_mode.title"));
        createimp$demandModeButton.getToolTip().add(
                Component.translatable("createimp.gui.factory_panel.demand_mode.desc")
                        .withStyle(ChatFormatting.GRAY));

        createimp$asAccessor().createimp$addRenderableWidgets(createimp$demandModeButton);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void createimp$tick(CallbackInfo ci) {
        if (createimp$demandModeButton == null) return;

        boolean shouldShow = !restocker && connections != null && !connections.isEmpty();
        createimp$demandModeButton.visible = shouldShow;
        createimp$demandModeButton.active = shouldShow;

        if (shouldShow) {
            createimp$demandModeButton.green =
                    ((IFactoryPanelBehaviourDemandMode) behaviour).createimp$isDemandMode();
        }
    }
}