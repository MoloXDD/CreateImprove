package com.molox.createimp.screen;

import com.molox.createimp.block.redstone_link_router.RedstoneLinkRouterBlockEntity;
import com.molox.createimp.registry.ModMenuTypes;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 路由器物品终端的配置菜单：以模板仪表配置物品界面（{@code TemplatePanelSetItemMenu}）
 * 为原型，把原本唯一的那个物品框，以它的中心为轴，拆成上下各一个——上面对应
 * {@code itemSlot1}，下面对应 {@code itemSlot2}。两个槽位都是"幽灵槽位"：point 点击/
 * 拖入只记录物品种类（数量固定为1，不会真的从背包扣除），复用 Create 自带的
 * {@link GhostItemMenu} 机制，JEI 拖拽通过 {@link #submitGhostItem} 接入。
 * <p>
 * 每次点击其中一个槽位（或者 JEI 拖拽落地）后立即把两个槽位的最新内容同步写回
 * {@link RedstoneLinkRouterBlockEntity}，不等菜单关闭——这样即使玩家不点确认键、
 * 直接按 Esc 关闭，数据也已经落到了方块实体上。
 */
public class RedstoneLinkRouterSetItemMenu extends GhostItemMenu<ItemStack> {

    public static final int ITEM_SLOT_1_X = 74;
    public static final int ITEM_SLOT_1_Y = 19;
    public static final int ITEM_SLOT_2_X = 74;
    public static final int ITEM_SLOT_2_Y = 37;

    public static final int ITEM_SLOT_1_INDEX = 36;
    public static final int ITEM_SLOT_2_INDEX = 37;

    public final BlockPos pos;
    public final int rowIndex;
    public final int slotIndex;

    public RedstoneLinkRouterSetItemMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(ModMenuTypes.REDSTONE_LINK_ROUTER_SET_ITEM.get(), id, inv,
                BlockPos.STREAM_CODEC.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public RedstoneLinkRouterSetItemMenu(MenuType<?> type, int id, Inventory inv,
                                         BlockPos pos, int rowIndex, int slotIndex,
                                         ItemStack initialItem1, ItemStack initialItem2) {
        super(type, id, inv, ItemStack.EMPTY);
        this.pos = pos;
        this.rowIndex = rowIndex;
        this.slotIndex = slotIndex;
        ghostInventory.setStackInSlot(0, initialItem1.copy());
        ghostInventory.setStackInSlot(1, initialItem2.copy());
    }

    @Override
    protected ItemStack createOnClient(RegistryFriendlyByteBuf buf) {
        return ItemStack.EMPTY;
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler(2);
    }

    @Override
    protected void addSlots() {
        int playerX = 13;
        int playerY = 112;
        addPlayerSlots(playerX, playerY);
        addSlot(new SlotItemHandler(ghostInventory, 0, ITEM_SLOT_1_X, ITEM_SLOT_1_Y));
        addSlot(new SlotItemHandler(ghostInventory, 1, ITEM_SLOT_2_X, ITEM_SLOT_2_Y));
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        super.clicked(slotId, dragType, clickType, player);
        if (slotId == ITEM_SLOT_1_INDEX || slotId == ITEM_SLOT_2_INDEX) {
            syncToBlockEntity(player);
        }
    }

    /** 供 JEI 拖拽处理器在服务端调用：把幽灵物品直接设置进指定槽位（0 或 1）并同步。 */
    public void submitGhostItem(int ghostSlot, ItemStack stack, Player player) {
        if (stack.isEmpty()) return;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        ghostInventory.setStackInSlot(ghostSlot, copy);
        syncToBlockEntity(player);
    }

    private void syncToBlockEntity(Player player) {
        if (player.level().isClientSide()) return;
        if (!(player.level().getBlockEntity(pos) instanceof RedstoneLinkRouterBlockEntity router)) return;
        router.setComponentItemSlots(rowIndex, slotIndex,
                ghostInventory.getStackInSlot(0).copy(),
                ghostInventory.getStackInSlot(1).copy());
    }

    @Override
    protected void saveData(ItemStack ignored) {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean allowRepeats() {
        return true;
    }
}