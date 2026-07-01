package com.molox.createimp.block.template_panel;

import com.molox.createimp.registry.ModBlocks;
import com.molox.createimp.registry.ModItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.createmod.catnip.codecs.CatnipCodecs;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.Tags;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TemplatePanelBehaviour extends FilteringBehaviour implements MenuProvider {

    public static final BehaviourType<TemplatePanelBehaviour> TOP_LEFT = new BehaviourType<>();
    public static final BehaviourType<TemplatePanelBehaviour> TOP_RIGHT = new BehaviourType<>();
    public static final BehaviourType<TemplatePanelBehaviour> BOTTOM_LEFT = new BehaviourType<>();
    public static final BehaviourType<TemplatePanelBehaviour> BOTTOM_RIGHT = new BehaviourType<>();

    public Map<TemplatePanelPosition, TemplatePanelConnection> targetedBy;
    public Set<TemplatePanelPosition> targeting;
    public List<ItemStack> activeCraftingArrangement;
    public String recipeAddress;
    public int recipeOutput;
    public TemplatePanelBlock.PanelSlot slot;
    public UUID network;
    public boolean active;
    public boolean demandMode;
    public int lastReportedLevelInStorage;

    public TemplatePanelBehaviour(TemplatePanelBlockEntity be, TemplatePanelBlock.PanelSlot slot) {
        super(be, new TemplatePanelSlotPositioning(slot));
        this.slot = slot;
        this.targetedBy = new HashMap<>();
        this.targeting = new HashSet<>();
        this.activeCraftingArrangement = List.of();
        this.recipeAddress = "";
        this.recipeOutput = 1;
        this.active = false;
        this.demandMode = false;
        this.lastReportedLevelInStorage = 0;
        this.network = UUID.randomUUID();
    }

    public void setNetwork(UUID network) {
        this.network = network;
    }

    public static TemplatePanelBehaviour at(BlockAndTintGetter world, TemplatePanelConnection connection) {
        Object cached = connection.cachedSource.get();
        if (cached instanceof TemplatePanelBehaviour tpb && !tpb.blockEntity.isRemoved()) {
            return tpb;
        }
        TemplatePanelBehaviour result = at(world, connection.from);
        connection.cachedSource = new java.lang.ref.WeakReference<>(result);
        return result;
    }

    public static TemplatePanelBehaviour at(BlockAndTintGetter world, TemplatePanelPosition pos) {
        if (world instanceof Level l && !l.isLoaded(pos.pos())) {
            return null;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos.pos());
        if (!(blockEntity instanceof TemplatePanelBlockEntity tpbe)) {
            return null;
        }
        TemplatePanelBehaviour behaviour = tpbe.panels.get(pos.slot());
        if (behaviour == null || !behaviour.active) {
            return null;
        }
        return behaviour;
    }

    public static ItemStack getExternalFilter(Level level, BlockPos pos, TemplatePanelBlock.PanelSlot slot) {
        if (!level.isLoaded(pos)) {
            return ItemStack.EMPTY;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TemplatePanelBlockEntity tpbe) {
            TemplatePanelBehaviour b = tpbe.panels.get(slot);
            return b != null && b.isActive() ? b.getFilter() : ItemStack.EMPTY;
        }
        if (be instanceof FactoryPanelBlockEntity fpbe) {
            FactoryPanelBlock.PanelSlot vanillaSlot = FactoryPanelBlock.PanelSlot.valueOf(slot.name());
            FactoryPanelBehaviour b = fpbe.panels.get(vanillaSlot);
            return b != null && b.isActive() ? b.getFilter() : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    public void moveTo(TemplatePanelPosition newPos, ServerPlayer player) {
        Level level = this.getWorld();
        BlockState existingState = level.getBlockState(newPos.pos());
        if (at(level, newPos) != null) {
            return;
        }
        boolean isAddedToOtherPanel = existingState.is(ModBlocks.TEMPLATE_PANEL.get());
        if (!existingState.isAir() && !isAddedToOtherPanel) {
            return;
        }
        if (isAddedToOtherPanel && existingState != this.blockEntity.getBlockState()) {
            return;
        }
        if (!isAddedToOtherPanel) {
            level.setBlock(newPos.pos(), this.blockEntity.getBlockState(), 3);
        }
        for (TemplatePanelPosition p : this.targetedBy.keySet()) {
            if (p.pos().closerThan((Vec3i) newPos.pos(), 24.0)) continue;
            return;
        }
        for (TemplatePanelPosition p : this.targeting) {
            if (p.pos().closerThan((Vec3i) newPos.pos(), 24.0)) continue;
            return;
        }
        SmartBlockEntity oldBE = this.blockEntity;
        TemplatePanelPosition oldPosition = this.getPanelPosition();
        this.moveToSlot(newPos.slot());
        BlockEntity blockEntity = level.getBlockEntity(newPos.pos());
        if (blockEntity instanceof TemplatePanelBlockEntity fpbe) {
            fpbe.attachBehaviourLate(this);
            fpbe.panels.put(this.slot, this);
            fpbe.redraw = true;
            fpbe.lastShape = null;
            fpbe.notifyUpdate();
        }
        if (oldBE instanceof TemplatePanelBlockEntity fpbe) {
            TemplatePanelBehaviour newBehaviour = new TemplatePanelBehaviour(fpbe, oldPosition.slot());
            fpbe.attachBehaviourLate(newBehaviour);
            fpbe.panels.put(oldPosition.slot(), newBehaviour);
            fpbe.redraw = true;
            fpbe.lastShape = null;
            fpbe.notifyUpdate();
        }
        for (TemplatePanelPosition position : this.targeting) {
            TemplatePanelBehaviour at = at(level, position);
            if (at == null) continue;
            TemplatePanelConnection connection = at.targetedBy.remove(oldPosition);
            if (connection == null) continue;
            connection.from = newPos;
            at.targetedBy.put(newPos, connection);
            at.blockEntity.sendData();
        }
        for (TemplatePanelPosition position : this.targetedBy.keySet()) {
            TemplatePanelBehaviour at = at(level, position);
            if (at == null) continue;
            at.targeting.remove(oldPosition);
            at.targeting.add(newPos);
        }
        player.displayClientMessage(CreateLang.translate("factory_panel.relocated").style(ChatFormatting.GREEN).component(), true);
        player.level().playSound(null, newPos.pos(), SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private void moveToSlot(TemplatePanelBlock.PanelSlot slot) {
        this.slot = slot;
        ValueBoxTransform valueBoxTransform = this.getSlotPositioning();
        if (valueBoxTransform instanceof TemplatePanelSlotPositioning fpsp) {
            fpsp.slot = slot;
        }
    }

    public void addConnection(TemplatePanelPosition fromPos) {
        if (this.targetedBy.size() >= 9) {
            return;
        }
        if (this.targetedBy.containsKey(fromPos)) {
            return;
        }
        Level level = this.getWorld();
        BlockEntity be = level.getBlockEntity(fromPos.pos());
        if (be instanceof TemplatePanelBlockEntity tpbe) {
            TemplatePanelBehaviour source = tpbe.panels.get(fromPos.slot());
            if (source == null || !source.isActive()) {
                return;
            }
            source.targeting.add(this.getPanelPosition());
            source.blockEntity.sendData();
        } else if (!(be instanceof FactoryPanelBlockEntity)) {
            return;
        }
        this.targetedBy.put(fromPos, new TemplatePanelConnection(fromPos, 1));
        this.blockEntity.notifyUpdate();
    }

    public TemplatePanelPosition getPanelPosition() {
        return new TemplatePanelPosition(this.getPos(), this.slot);
    }

    public TemplatePanelBlockEntity panelBE() {
        return (TemplatePanelBlockEntity) this.blockEntity;
    }

    @Override
    public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
        if (!Create.LOGISTICS.mayInteract(this.network, player)) {
            player.displayClientMessage(CreateLang.translate("logistically_linked.protected").style(ChatFormatting.RED).component(), true);
            return;
        }
        boolean isClientSide = player.level().isClientSide;
        if (!this.targeting.isEmpty() && player.getItemInHand(hand).is(Tags.Items.TOOLS_WRENCH)) {
            int sharedMode = -1;
            for (TemplatePanelPosition target : this.targeting) {
                TemplatePanelBehaviour at = at(this.getWorld(), target);
                if (at == null) continue;
                TemplatePanelConnection connection = at.targetedBy.get(this.getPanelPosition());
                if (connection == null) continue;
                if (sharedMode == -1) {
                    sharedMode = (connection.arrowBendMode + 1) % 4;
                }
                connection.arrowBendMode = sharedMode;
                if (isClientSide) continue;
                at.blockEntity.notifyUpdate();
            }
            if (sharedMode == -1) {
                return;
            }
            char[] boxes = "\u25a1\u25a1\u25a1\u25a1".toCharArray();
            boxes[sharedMode] = '\u25a0';
            player.displayClientMessage(CreateLang.translate("factory_panel.cycled_arrow_path", new String(boxes)).component(), true);
            return;
        }
        if (isClientSide && com.molox.createimp.block.template_panel.TemplatePanelConnectionHandler.panelClicked((LevelAccessor) this.getWorld(), player, this)) {
            return;
        }
        ItemStack heldItem = player.getItemInHand(hand);
        if (this.getFilter().isEmpty()) {
            if (heldItem.isEmpty()) {
                if (!isClientSide && player instanceof ServerPlayer sp) {
                    sp.openMenu(this, buf -> TemplatePanelPosition.STREAM_CODEC.encode(buf, this.getPanelPosition()));
                }
                return;
            }
            super.onShortInteract(player, hand, side, hitResult);
            return;
        }
        if (heldItem.getItem() instanceof LogisticallyLinkedBlockItem) {
            if (!isClientSide) {
                LogisticallyLinkedBlockItem.assignFrequency(heldItem, player, this.network);
            }
            return;
        }
        if (isClientSide) {
            CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> this.displayScreen(player));
        }
    }

    public void enable() {
        this.active = true;
        this.blockEntity.notifyUpdate();
    }

    public void disable() {
        this.destroy();
        this.active = false;
        this.targetedBy = new HashMap<>();
        this.targeting = new HashSet<>();
        this.recipeAddress = "";
        this.recipeOutput = 1;
        this.demandMode = false;
        this.lastReportedLevelInStorage = 0;
        this.setFilter(ItemStack.EMPTY);
        this.blockEntity.notifyUpdate();
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public void destroy() {
        this.disconnectAll();
        super.destroy();
    }

    public void disconnectAll() {
        TemplatePanelPosition panelPosition = this.getPanelPosition();
        for (TemplatePanelConnection connection : this.targetedBy.values()) {
            TemplatePanelBehaviour source = at(this.getWorld(), connection);
            if (source == null) continue;
            source.targeting.remove(panelPosition);
            source.blockEntity.sendData();
        }
        for (TemplatePanelPosition position : this.targeting) {
            TemplatePanelBehaviour target = at(this.getWorld(), position);
            if (target == null) continue;
            target.targetedBy.remove(panelPosition);
            target.blockEntity.sendData();
        }
        this.targetedBy.clear();
        this.targeting.clear();
    }

    @Override
    public void writeSafe(CompoundTag nbt, HolderLookup.Provider registries) {
        if (!this.active) {
            return;
        }
        CompoundTag panelTag = new CompoundTag();
        panelTag.put("Filter", this.getFilter().saveOptional(registries));
        panelTag.putUUID("Freq", this.network);
        panelTag.putString("RecipeAddress", this.recipeAddress);
        panelTag.putInt("RecipeOutput", this.recipeOutput);
        nbt.put(CreateLang.asId(this.slot.name()), panelTag);
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        if (!this.active) {
            return;
        }
        if (this.targetedBy.isEmpty()) {
            this.demandMode = false;
        }
        CompoundTag panelTag = new CompoundTag();
        super.write(panelTag, registries, clientPacket);
        panelTag.put("Targeting", CatnipCodecUtils.encode(CatnipCodecs.set(TemplatePanelPosition.CODEC), registries, this.targeting).orElseThrow());
        panelTag.put("TargetedBy", CatnipCodecUtils.encode(com.mojang.serialization.Codec.list(TemplatePanelConnection.CODEC), registries, new java.util.ArrayList<>(this.targetedBy.values())).orElseThrow());
        panelTag.putString("RecipeAddress", this.recipeAddress);
        panelTag.putInt("RecipeOutput", this.recipeOutput);
        panelTag.putUUID("Freq", this.network);
        panelTag.putBoolean("DemandMode", this.demandMode);
        panelTag.putInt("LevelInStorage", this.lastReportedLevelInStorage);
        panelTag.put("Craft", net.createmod.catnip.nbt.NBTHelper.writeItemList(this.activeCraftingArrangement, registries));
        nbt.put(CreateLang.asId(this.slot.name()), panelTag);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        CompoundTag panelTag = nbt.getCompound(CreateLang.asId(this.slot.name()));
        if (panelTag.isEmpty()) {
            this.active = false;
            return;
        }
        this.active = true;
        this.filter = FilterItemStack.of(registries, panelTag.getCompound("Filter"));
        if (panelTag.hasUUID("Freq")) {
            this.network = panelTag.getUUID("Freq");
        }
        this.demandMode = panelTag.getBoolean("DemandMode");
        this.lastReportedLevelInStorage = panelTag.getInt("LevelInStorage");
        this.targeting.clear();
        this.targeting.addAll(CatnipCodecUtils.decode(CatnipCodecs.set(TemplatePanelPosition.CODEC), registries, panelTag.get("Targeting")).orElse(Set.of()));
        this.targetedBy.clear();
        CatnipCodecUtils.decode(com.mojang.serialization.Codec.list(TemplatePanelConnection.CODEC), registries, (Tag) panelTag.get("TargetedBy")).orElse(List.of())
                .forEach(c -> this.targetedBy.put(c.from, c));
        this.activeCraftingArrangement = net.createmod.catnip.nbt.NBTHelper.readItemList((ListTag) panelTag.getList("Craft", 10), registries);
        this.recipeAddress = panelTag.getString("RecipeAddress");
        this.recipeOutput = panelTag.getInt("RecipeOutput");
    }

    @Override
    public float getRenderDistance() {
        return 64.0f;
    }

    @Override
    public boolean acceptsValueSettings() {
        return false;
    }

    @Override
    public boolean isCountVisible() {
        return !this.getFilter().isEmpty();
    }

    @Override
    public MutableComponent formatValue(ValueSettingsBehaviour.ValueSettings value) {
        return Component.empty();
    }

    @Override
    public boolean setFilter(ItemStack stack) {
        ItemStack filter = stack.copy();
        if (stack.getItem() instanceof FilterItem) {
            return false;
        }
        this.filter = FilterItemStack.of(filter);
        this.blockEntity.setChanged();
        this.blockEntity.sendData();
        return true;
    }

    @Override
    public void setValueSettings(Player player, ValueSettingsBehaviour.ValueSettings settings, boolean ctrlDown) {
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        return null;
    }

    @Override
    public MutableComponent getLabel() {
        if (this.getFilter().isEmpty()) {
            return CreateLang.translate("factory_panel.new_factory_task").component();
        }
        return this.getFilter().getHoverName().plainCopy();
    }

    @Override
    public ValueSettingsBehaviour.ValueSettings getValueSettings() {
        return new ValueSettingsBehaviour.ValueSettings(0, 0);
    }

    @Override
    public MutableComponent getTip() {
        return CreateLang.translateDirect(this.filter.isEmpty() ? "logistics.filter.click_to_set" : "factory_panel.click_to_configure");
    }

    @Override
    public MutableComponent getAmountTip() {
        return Component.empty();
    }

    @Override
    public MutableComponent getCountLabelForValueBox() {
        if (this.filter.isEmpty()) {
            return Component.empty();
        }
        int levelInStorage = this.getLevelInStorage();
        boolean inf = levelInStorage >= 1000000000;
        int inStorage = levelInStorage / (this.upTo ? 1 : this.getFilter().getMaxStackSize());
        String stacks = this.upTo ? "" : "\u25a4";
        return CreateLang.text(inf ? "  \u221e" : inStorage + stacks).color(15855592).component();
    }

    public int getLevelInStorage() {
        if (this.blockEntity.isVirtual()) {
            return 1;
        }
        if (this.getWorld().isClientSide()) {
            return this.lastReportedLevelInStorage;
        }
        if (this.getFilter().isEmpty()) {
            return 0;
        }
        return com.simibubi.create.content.logistics.packagerLink.LogisticsManager
                .getSummaryOfNetwork(this.network, false).getCountOf(this.getFilter());
    }

    private void tickStorageMonitor() {
        if (this.getWorld().isClientSide()) {
            return;
        }
        int inStorage = this.getLevelInStorage();
        if (this.lastReportedLevelInStorage == inStorage) {
            return;
        }
        this.lastReportedLevelInStorage = inStorage;
        this.blockEntity.sendData();
    }

    @Override
    public void tick() {
        super.tick();
        this.tickStorageMonitor();
    }

    @Override
    public int netId() {
        return 2 + this.slot.ordinal();
    }

    @Override
    public BehaviourType<?> getType() {
        return getTypeForSlot(this.slot);
    }

    public static BehaviourType<?> getTypeForSlot(TemplatePanelBlock.PanelSlot slot) {
        return switch (slot) {
            case TOP_LEFT -> TOP_LEFT;
            case TOP_RIGHT -> TOP_RIGHT;
            case BOTTOM_LEFT -> BOTTOM_LEFT;
            case BOTTOM_RIGHT -> BOTTOM_RIGHT;
        };
    }

    @OnlyIn(Dist.CLIENT)
    public void displayScreen(Player player) {
        if (player instanceof LocalPlayer) {
            ScreenOpener.open(new com.molox.createimp.screen.TemplatePanelScreen(this));
        }
    }

    public int getIngredientStatusColor() {
        return 0x888898;
    }

    @Override
    public ItemRequirement getRequiredItems() {
        return this.isActive() ? new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, ModItems.TEMPLATE_PANEL.get()) : ItemRequirement.NONE;
    }

    @Override
    public boolean canShortInteract(ItemStack toApply) {
        return true;
    }

    @Override
    public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate) {
        return false;
    }

    @Override
    public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        return false;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return com.molox.createimp.screen.TemplatePanelSetItemMenu.create(containerId, playerInventory, this);
    }

    @Override
    public Component getDisplayName() {
        return this.blockEntity.getBlockState().getBlock().getName();
    }
}