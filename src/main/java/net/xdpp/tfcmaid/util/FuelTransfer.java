package net.xdpp.tfcmaid.util;

import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.common.blocks.devices.FirepitBlock;
import net.dries007.tfc.util.Fuel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public final class FuelTransfer {
    private FuelTransfer() {}

    public static boolean transfer(BlockEntity device, IItemHandler backpack, boolean simulate) {
        if (device == null || device.isRemoved()) {
            return false;
        }
        var state = device.getBlockState();
        int first, last;
        if (device instanceof AbstractFirepitBlockEntity<?> && state.hasProperty(FirepitBlock.LIT)
                && state.getValue(FirepitBlock.LIT)) {
            first = last = AbstractFirepitBlockEntity.SLOT_FUEL_INPUT;
        } else if (device instanceof CharcoalForgeBlockEntity && state.hasProperty(CharcoalForgeBlock.HEAT)
                && state.getValue(CharcoalForgeBlock.HEAT) > 0) {
            first = CharcoalForgeBlockEntity.SLOT_FUEL_MIN;
            last = CharcoalForgeBlockEntity.SLOT_FUEL_MAX;
        } else {
            return false;
        }
        // Unsided access models manual insertion without requiring hopper automation.
        var target = device.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (target == null || target.getSlots() <= last) {
            return false;
        }
        for (int from = 0; from < backpack.getSlots(); from++) {
            ItemStack candidate = backpack.extractItem(from, 1, true);
            if (candidate.isEmpty() || Fuel.get(candidate) == null) {
                continue;
            }
            for (int to = first; to <= last; to++) {
                if (!target.insertItem(to, candidate, true).isEmpty()) {
                    continue;
                }
                if (simulate) {
                    return true;
                }
                ItemStack extracted = backpack.extractItem(from, 1, false);
                if (extracted.isEmpty()) {
                    return false;
                }
                ItemStack remainder = target.insertItem(to, extracted, false);
                if (!remainder.isEmpty()) {
                    backpack.insertItem(from, remainder, false);
                    return false;
                }
                device.setChanged();
                return true;
            }
        }
        return false;
    }
}
