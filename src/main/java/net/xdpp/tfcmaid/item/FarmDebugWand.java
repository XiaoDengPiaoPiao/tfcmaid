package net.xdpp.tfcmaid.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.dries007.tfc.common.blockentities.CropBlockEntity;
import net.dries007.tfc.common.blocks.crop.ICropBlock;
import net.dries007.tfc.util.calendar.Calendars;

public class FarmDebugWand extends Item {

    public FarmDebugWand(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof ICropBlock cropBlock) {
            if (level.getBlockEntity(pos) instanceof CropBlockEntity crop) {
                crop.setGrowth(1.0f);
                crop.setYield(1.0f);
                crop.setExpiry(0.0f);
                crop.setLastGrowthTick(Calendars.get(level).getTicks());
                crop.markForSync();

                int maxAge = ((net.dries007.tfc.common.blocks.crop.CropBlock) block).getMaxAge();
                BlockState newState = state.setValue(((net.dries007.tfc.common.blocks.crop.CropBlock) block).getAgeProperty(), maxAge);
                level.setBlockAndUpdate(pos, newState);

                Player player = context.getPlayer();
                if (player != null) {
                    player.displayClientMessage(Component.literal("作物已加速成熟！"), true);
                }
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("右键点击作物可使其立即成熟且不会枯萎"), true);
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
