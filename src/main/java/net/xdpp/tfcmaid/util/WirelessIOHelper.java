package net.xdpp.tfcmaid.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.WirelessIOBauble;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import static com.github.tartaricacid.touhoulittlemaid.util.BytesBooleansConvert.bytes2Booleans;

// 隙间工具类，把几个Behavior里重复的隙间操作都抽到这里来了
// 省得每个文件都要写一遍获取饰品、检查绑定、过滤物品这些破事
public class WirelessIOHelper {

    // 从女仆饰品栏里找隙间，找不到就返回空
    public static ItemStack getWirelessIOBauble(EntityMaid maid) {
        var baubleHandler = maid.getMaidBauble();
        for (int i = 0; i < baubleHandler.getSlots(); i++) {
            ItemStack stack = baubleHandler.getStackInSlot(i);
            if (stack.is(InitItems.WIRELESS_IO.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // 检查女仆有没有装备隙间并且绑定了箱子
    // 两个条件都满足才返回true
    public static boolean hasWirelessIOAndBound(EntityMaid maid) {
        ItemStack wirelessIO = getWirelessIOBauble(maid);
        if (wirelessIO.isEmpty()) {
            return false;
        }
        var bindingPos = ItemWirelessIO.getBindingPos(wirelessIO);
        return bindingPos != null && maid.isWithinRestriction(bindingPos);
    }

    // 尝试把物品塞到隙间绑定的箱子里
    // 会自动处理过滤规则，塞不进去就原样返回
    public static ItemStack tryInsertToChest(EntityMaid maid, ItemStack stack) {
        ItemStack wirelessIO = getWirelessIOBauble(maid);
        if (wirelessIO.isEmpty()) {
            return stack;
        }

        var bindingPos = ItemWirelessIO.getBindingPos(wirelessIO);
        if (bindingPos == null || !maid.isWithinRestriction(bindingPos)) {
            return stack;
        }

        BlockEntity te = maid.level().getBlockEntity(bindingPos);
        if (te == null) {
            return stack;
        }

        // 遍历所有支持的箱子类型，找到能用的就行
        for (var type : ChestManager.getAllChestTypes()) {
            if (type.isChest(te)) {
                IItemHandler chestInv = te.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
                if (chestInv != null) {
                    return tryInsertToChestWithFilter(wirelessIO, chestInv, stack);
                }
                break;
            }
        }
        return stack;
    }

    // 带过滤规则的插入方法，这里会检查黑白名单
    // 不要直接调用这个，用上面那个tryInsertToChest
    public static ItemStack tryInsertToChestWithFilter(ItemStack wirelessIO, IItemHandler chestInv, ItemStack stack) {
        boolean isBlacklist = ItemWirelessIO.isBlacklist(wirelessIO);
        IItemHandler filterList = ItemWirelessIO.getFilterList(wirelessIO);
        byte[] slotConfig = ItemWirelessIO.getSlotConfig(wirelessIO);
        // 动态获取槽位数：slotConfig有就用它的长度，没有就用默认38（女仆标准槽位数）
        int slotNum = slotConfig != null ? slotConfig.length : 38;
        boolean[] slotConfigData = slotConfig != null ? bytes2Booleans(slotConfig, slotNum) : null;

        // 先检查物品能不能移动，根据黑白名单判断
        boolean allowMove = isBlacklist;
        for (int j = 0; j < filterList.getSlots(); j++) {
            ItemStack filterItem = filterList.getStackInSlot(j);
            boolean isEqual = ItemStack.isSameItem(stack, filterItem);
            if (isEqual) {
                allowMove = !isBlacklist;
                break;
            }
        }

        if (allowMove) {
            return WirelessIOBauble.insertItemStacked(chestInv, stack, false, slotConfigData);
        }
        return stack;
    }

    // 单独检查某个物品是否允许被隙间移动
    // 黑白名单逻辑都在这里，避免重复写
    public static boolean isItemAllowed(ItemStack wirelessIO, ItemStack stack) {
        boolean isBlacklist = ItemWirelessIO.isBlacklist(wirelessIO);
        IItemHandler filterList = ItemWirelessIO.getFilterList(wirelessIO);

        boolean allowMove = isBlacklist;
        for (int j = 0; j < filterList.getSlots(); j++) {
            ItemStack filterItem = filterList.getStackInSlot(j);
            boolean isEqual = ItemStack.isSameItem(stack, filterItem);
            if (isEqual) {
                allowMove = !isBlacklist;
                break;
            }
        }
        return allowMove;
    }

    // 获取绑定箱子的物品处理器，懒得写就直接调用这个
    public static IItemHandler getChestHandler(EntityMaid maid) {
        ItemStack wirelessIO = getWirelessIOBauble(maid);
        if (wirelessIO.isEmpty()) {
            return null;
        }

        var bindingPos = ItemWirelessIO.getBindingPos(wirelessIO);
        if (bindingPos == null || !maid.isWithinRestriction(bindingPos)) {
            return null;
        }

        BlockEntity te = maid.level().getBlockEntity(bindingPos);
        if (te == null) {
            return null;
        }

        for (var type : ChestManager.getAllChestTypes()) {
            if (type.isChest(te)) {
                return te.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            }
        }
        return null;
    }
}
