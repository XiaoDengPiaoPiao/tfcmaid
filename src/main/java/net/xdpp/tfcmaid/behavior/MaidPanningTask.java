package net.xdpp.tfcmaid.behavior;


import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.dries007.tfc.client.TFCSounds;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.items.EmptyPanItem;
import net.dries007.tfc.common.items.PanItem;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Pannable;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 女仆使用淘金盘进行筛矿的行为任务
 * 工作流程：
 * 1. 检查主手是否持有空淘金盘或已填充的淘金盘
 * 2. 如果是空淘金盘，尝试从背包中找到已填充的淘金盘或可筛的方块物品来填充
 * 3. 检查附近 4 格内是否有水源
 * 4. 没有水源则寻路到工作范围内的水边
 * 5. 到达水源后开始筛矿，持续 PanItem.USE_TIME 个 tick
 * 6. 筛矿完成后生成掉落物并分发，淘金盘变回空的，继续下一个循环
 */
public class MaidPanningTask extends MaidLongRunningTask {
    private static final int PAN_USE_TIME = PanItem.USE_TIME;
    private final float speedModifier;
    @Nullable
    private BlockPos waterPos = null;
    private int panningProgress = 0;

    public MaidPanningTask(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        ItemStack mainHand = maid.getMainHandItem();
        return mainHand.getItem() instanceof EmptyPanItem || mainHand.getItem() instanceof PanItem;
    }

    @Override
    protected boolean canStillUse(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        if (panningProgress > 0) {
            return true;
        }
        ItemStack mainHand = maid.getMainHandItem();
        return mainHand.getItem() instanceof EmptyPanItem || mainHand.getItem() instanceof PanItem;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        ItemStack mainHand = maid.getMainHandItem();

        if (mainHand.getItem() instanceof EmptyPanItem) {
            equipFilledPanFromInventory(maid);
            if (maid.getMainHandItem().getItem() instanceof EmptyPanItem) {
                return;
            }
        }
    }

    @Override
    protected void tick(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        if (panningProgress > 0) {
            continuePanning(worldIn, maid);
            return;
        }

        ItemStack mainHand = maid.getMainHandItem();
        if (!(mainHand.getItem() instanceof PanItem)) {
            return;
        }

        if (!isNearWater(maid)) {
            if (waterPos == null) {
                waterPos = findNearbyWater(maid);
                if (waterPos != null) {
                    BlockPos safePos = findSafeStandingPos(worldIn, waterPos);
                    if (safePos != null) {
                        BehaviorUtils.setWalkAndLookTargetMemories(maid, safePos, speedModifier, 0);
                    }
                }
            }
            return;
        }

        waterPos = null;
        startPanning(worldIn, maid);
    }

    @Override
    protected void stop(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        panningProgress = 0;
        waterPos = null;
    }

    /**
     * 检查女仆附近 4 格内是否有水源
     */
    private boolean isNearWater(EntityMaid maid) {
        BlockPos maidPos = maid.blockPosition();
        for (BlockPos pos : BlockPos.withinManhattan(maidPos, 4, 1, 4)) {
            if (maid.level().getFluidState(pos).is(TFCTags.Fluids.ANY_INFINITE_WATER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在女仆工作范围内寻找水源，范围从 4 格到 16 格
     */
    @Nullable
    private BlockPos findNearbyWater(EntityMaid maid) {
        BlockPos maidPos = maid.blockPosition();
        for (int i = 4; i <= 16; i++) {
            for (BlockPos pos : BlockPos.withinManhattan(maidPos, i, 2, i)) {
                if (maid.isWithinRestriction(pos) && maid.level().getFluidState(pos).is(TFCTags.Fluids.ANY_INFINITE_WATER)) {
                    return pos;
                }
            }
        }
        return null;
    }

    /**
     * 在水源附近寻找一个安全的站立位置（非水中且地面固体）
     */
    @Nullable
    private BlockPos findSafeStandingPos(ServerLevel world, BlockPos waterPos) {
        for (BlockPos pos : BlockPos.withinManhattan(waterPos, 4, 1, 4)) {
            if (world.getFluidState(pos).isEmpty() && world.getBlockState(pos.below()).canOcclude() && world.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }

    /**
     * 开始筛矿，初始化筛矿进度
     */
    private void startPanning(ServerLevel world, EntityMaid maid) {
        ItemStack stack = maid.getMainHandItem();
        if (!(stack.getItem() instanceof PanItem)) {
            return;
        }
        panningProgress = PAN_USE_TIME;
    }

    /**
     * 持续筛矿，减少进度，播放声音，进度归零时完成筛矿
     */
    private void continuePanning(ServerLevel world, EntityMaid maid) {
        panningProgress--;
        
        if (panningProgress % 16 == 0) {
            world.playSound(null, maid, TFCSounds.PANNING.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        if (panningProgress <= 0) {
            finishUsingPan(world, maid);
            panningProgress = 0;
        }
    }

    /**
     * 完成筛矿，生成掉落物并分发，淘金盘变回空的
     */
    private void finishUsingPan(ServerLevel world, EntityMaid maid) {
        ItemStack stack = maid.getMainHandItem();
        if (!(stack.getItem() instanceof PanItem)) {
            return;
        }

        final Pannable pannable = PanItem.readPannable(world.holderLookup(Registries.BLOCK), stack);
        if (pannable != null) {
            final var table = world.getServer().getLootData().getLootTable(pannable.getLootTable());
            final var builder = new LootParams.Builder(world)
                    .withParameter(LootContextParams.THIS_ENTITY, maid)
                    .withParameter(LootContextParams.ORIGIN, maid.position())
                    .withParameter(LootContextParams.TOOL, stack);
            final List<ItemStack> items = table.getRandomItems(builder.create(LootContextParamSets.FISHING));
            items.forEach(item -> ItemsUtil.giveItemToMaid(maid, item));
        }

        maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TFCItems.EMPTY_PAN.get()));
        equipFilledPanFromInventory(maid);
    }

    /**
     * 从背包中找到已填充的淘金盘并装备到主手
     * 如果没有已填充的淘金盘，则尝试用背包中的可筛方块物品填充空淘金盘
     */
    private void equipFilledPanFromInventory(EntityMaid maid) {
        for (int i = 0; i < maid.getMaidInv().getSlots(); i++) {
            ItemStack stack = maid.getMaidInv().getStackInSlot(i);
            if (stack.getItem() instanceof PanItem) {
                ItemStack mainHand = maid.getMainHandItem().copy();
                maid.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                maid.getMaidInv().setStackInSlot(i, mainHand);
                return;
            }
        }
        fillEmptyPanFromInventory(maid);
    }

    /**
     * 从背包中找到可筛的方块物品，消耗一个来填充空淘金盘
     */
    private void fillEmptyPanFromInventory(EntityMaid maid) {
        for (int i = 0; i < maid.getMaidInv().getSlots(); i++) {
            ItemStack stack = maid.getMaidInv().getStackInSlot(i);
            if (stack.getItem() instanceof BlockItem blockItem) {
                BlockState state = blockItem.getBlock().defaultBlockState();
                Pannable pannable = Pannable.get(state);
                if (pannable != null) {
                    stack.shrink(1);
                    ItemStack filledPan = new ItemStack(TFCItems.FILLED_PAN.get());
                    filledPan.addTagElement("state", NbtUtils.writeBlockState(state));
                    maid.setItemInHand(InteractionHand.MAIN_HAND, filledPan);
                    return;
                }
            }
        }
    }
}
