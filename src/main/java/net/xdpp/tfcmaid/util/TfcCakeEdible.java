package net.xdpp.tfcmaid.util;

import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;

import static com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock.belowIsSnackStand;

public class TfcCakeEdible implements IMaidEdibleBlock {
    @Override
    public boolean shouldMoveTo(EntityMaid maid, BlockPos pos, BlockState state) {
        if (state.is(TFCBlocks.CAKE.get())) {
            return belowIsSnackStand(maid, pos);
        }
        return false;
    }

    @Override
    public int getFavorabilityPoints(EntityMaid maid, BlockPos pos, BlockState state) {
        return 1;
    }

    @Override
    public boolean consume(EntityMaid maid, BlockPos pos, BlockState state) {
        int bites = state.getValue(CakeBlock.BITES);
        Level level = maid.level();
        if (bites < CakeBlock.MAX_BITES) {
            int currentBites = Math.min(bites + 1, CakeBlock.MAX_BITES);
            level.setBlock(pos, state.setValue(CakeBlock.BITES, currentBites), Block.UPDATE_ALL);
        } else {
            level.removeBlock(pos, false);
        }
        maid.spawnItemParticles(new ItemStack(TFCBlocks.CAKE.get()), 8);
        maid.playSound(SoundEvents.GENERIC_EAT);
        return true;
    }

    @Override
    public boolean canPlaceAsFood(EntityMaid maid, ItemStack stack, int slotIndex) {
        return stack.is(TFCBlocks.CAKE.get().asItem());
    }
}
