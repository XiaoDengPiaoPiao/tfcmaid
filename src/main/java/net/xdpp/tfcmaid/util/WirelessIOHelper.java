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

public class WirelessIOHelper {
    private static final int SLOT_NUM = 38;

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

    public static boolean hasWirelessIOAndBound(EntityMaid maid) {
        ItemStack wirelessIO = getWirelessIOBauble(maid);
        if (wirelessIO.isEmpty()) {
            return false;
        }
        var bindingPos = ItemWirelessIO.getBindingPos(wirelessIO);
        return bindingPos != null && maid.isWithinRestriction(bindingPos);
    }

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

    public static ItemStack tryInsertToChestWithFilter(ItemStack wirelessIO, IItemHandler chestInv, ItemStack stack) {
        boolean isBlacklist = ItemWirelessIO.isBlacklist(wirelessIO);
        IItemHandler filterList = ItemWirelessIO.getFilterList(wirelessIO);
        byte[] slotConfig = ItemWirelessIO.getSlotConfig(wirelessIO);
        boolean[] slotConfigData = slotConfig != null ? bytes2Booleans(slotConfig, SLOT_NUM) : null;

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
