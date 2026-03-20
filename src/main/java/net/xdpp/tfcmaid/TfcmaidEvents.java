package net.xdpp.tfcmaid;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidWirelessIOEvent;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * TFCMaid 事件监听器
 * 用于监听和处理与隙间（wireless_io）的物品传输事件
 * 功能：
 * 1. 当隙间从箱子往女仆背包传输物品时，跳过腐烂的食物
 * 2. 当隙间从女仆往箱子传输物品时，强制把腐烂的食物移动到箱子
 */
@Mod.EventBusSubscriber(modid = Tfcmaid.MODID)
public class TfcmaidEvents {

    /**
     * 监听隙间从箱子到女仆的物品传输事件
     * 功能：
     * 1. 遍历箱子中的每个槽位
     * 2. 如果物品是腐烂的，则跳过，不传输到女仆背包
     * 3. 保持原有的过滤器和槽位配置逻辑
     *
     * @param event 隙间从箱子到女仆的事件对象
     */
    @SubscribeEvent
    public static void onMaidWirelessIOChestToMaid(MaidWirelessIOEvent.ChestToMaid event) {
        IItemHandler chestInv = event.getChestInv();
        IItemHandler maidInv = event.getMaidInv();
        boolean isBlacklist = event.isBlacklist();
        IItemHandler filterList = event.getFilterInv();
        boolean[] slotConfig = event.getSlotConfig();

        for (int i = 0; i < chestInv.getSlots(); i++) {
            ItemStack chestInvStack = chestInv.getStackInSlot(i);
            if (chestInvStack.isEmpty()) {
                continue;
            }
            if (FoodCapability.isRotten(chestInvStack)) {
                continue;
            }
            boolean allowMove = isBlacklist;
            for (int j = 0; j < filterList.getSlots(); j++) {
                ItemStack filterItem = filterList.getStackInSlot(j);
                boolean isEqual = ItemStack.isSameItem(chestInvStack, filterItem);
                if (isEqual) {
                    allowMove = !isBlacklist;
                    break;
                }
            }
            if (allowMove) {
                int beforeCount = chestInvStack.getCount();
                ItemStack after = insertItemStacked(maidInv, chestInvStack.copy(), false, slotConfig);
                int afterCount = after.getCount();
                if (beforeCount != afterCount) {
                    chestInv.extractItem(i, beforeCount - afterCount, false);
                }
            }
        }
        event.setCanceled(true);
    }

    /**
     * 监听隙间从女仆到箱子的物品传输事件
     * 功能：
     * 1. 遍历女仆背包的每个槽位
     * 2. 如果物品是腐烂的，则强制移动到箱子，不管过滤器设置
     * 3. 保持原有的过滤器和槽位配置逻辑
     *
     * @param event 隙间从女仆到箱子的事件对象
     */
    @SubscribeEvent
    public static void onMaidWirelessIOMaidToChest(MaidWirelessIOEvent.MaidToChest event) {
        IItemHandler maidInv = event.getMaidInv();
        IItemHandler chestInv = event.getChestInv();
        boolean isBlacklist = event.isBlacklist();
        IItemHandler filterList = event.getFilterInv();
        boolean[] slotConfig = event.getSlotConfig();

        for (int i = 0; i < maidInv.getSlots(); i++) {
            if (i < slotConfig.length && slotConfig[i]) {
                continue;
            }
            ItemStack maidInvItem = maidInv.getStackInSlot(i);
            if (maidInvItem.isEmpty()) {
                continue;
            }
            boolean allowMove = isBlacklist || FoodCapability.isRotten(maidInvItem);
            if (!FoodCapability.isRotten(maidInvItem)) {
                for (int j = 0; j < filterList.getSlots(); j++) {
                    ItemStack filterItem = filterList.getStackInSlot(j);
                    boolean isEqual = ItemStack.isSameItem(maidInvItem, filterItem);
                    if (isEqual) {
                        allowMove = !isBlacklist;
                        break;
                    }
                }
            }
            if (allowMove) {
                int beforeCount = maidInvItem.getCount();
                ItemStack after = ItemHandlerHelper.insertItemStacked(chestInv, maidInvItem.copy(), false);
                int afterCount = after.getCount();
                if (beforeCount != afterCount) {
                    maidInv.extractItem(i, beforeCount - afterCount, false);
                }
            }
        }
        event.setCanceled(true);
    }

    /**
     * 批量插入物品到物品栏（支持堆叠）
     * 先尝试堆叠到已有物品上，再尝试放到空槽位
     *
     * @param inventory 目标物品栏
     * @param stack 要插入的物品
     * @param simulate 是否模拟插入（true表示不实际修改物品）
     * @param slotConfig 槽位配置，true表示该槽位不允许插入
     * @return 剩余未能插入的物品
     */
    private static ItemStack insertItemStacked(IItemHandler inventory, ItemStack stack, boolean simulate, boolean[] slotConfig) {
        if (stack.isEmpty()) {
            return stack;
        }
        if (!stack.isStackable()) {
            return insertItem(inventory, stack, simulate, slotConfig);
        }
        int sizeInventory = inventory.getSlots();
        for (int i = 0; i < sizeInventory; i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slotConfig != null && i < slotConfig.length && slotConfig[i]) {
                continue;
            }
            if (canItemStacksStackRelaxed(slot, stack)) {
                stack = inventory.insertItem(i, stack, simulate);
                if (stack.isEmpty()) {
                    break;
                }
            }
        }

        if (!stack.isEmpty()) {
            for (int i = 0; i < sizeInventory; i++) {
                if (slotConfig != null && i < slotConfig.length && slotConfig[i]) {
                    continue;
                }
                if (inventory.getStackInSlot(i).isEmpty()) {
                    stack = inventory.insertItem(i, stack, simulate);
                    if (stack.isEmpty()) {
                        break;
                    }
                }
            }
        }

        return stack;
    }

    /**
     * 插入单个物品到物品栏（不尝试堆叠）
     *
     * @param dest 目标物品栏
     * @param stack 要插入的物品
     * @param simulate 是否模拟插入（true表示不实际修改物品）
     * @param slotConfig 槽位配置，true表示该槽位不允许插入
     * @return 剩余未能插入的物品
     */
    private static ItemStack insertItem(IItemHandler dest, ItemStack stack, boolean simulate, boolean[] slotConfig) {
        if (stack.isEmpty()) {
            return stack;
        }
        for (int i = 0; i < dest.getSlots(); i++) {
            if (slotConfig != null && i < slotConfig.length && slotConfig[i]) {
                continue;
            }
            stack = dest.insertItem(i, stack, simulate);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /**
     * 检查两个物品是否可以堆叠（宽松判断）
     *
     * @param a 第一个物品
     * @param b 第二个物品
     * @return true表示可以堆叠
     */
    private static boolean canItemStacksStackRelaxed(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty() || a.getItem() != b.getItem()) {
            return false;
        }
        if (!a.isStackable()) {
            return false;
        }
        if (a.hasTag() != b.hasTag()) {
            return false;
        }
        return (!a.hasTag() || a.getTag().equals(b.getTag())) && a.areCapsCompatible(b);
    }
}
