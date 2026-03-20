package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableMap;
import net.dries007.tfc.common.blockentities.BellowsBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 风箱工作任务类
 * 继承自 MaidLongRunningTask，实现拉风箱操作的完整流程
 * 工作流程：
 * 1. 走到风箱4格范围内
 * 2. 检查风箱是否可以拉风箱（是否连接了曲轴，或者是否在冷却时间）
 * 3. 调用风箱的 onRightClick() 方法进行拉风箱
 * 功能特点：
 * - 每20 tick（1秒）可以拉风箱一次
 * - 距离检查：只有在风箱4格范围内才能工作
 */
public class MaidBellowsWorkTask extends MaidLongRunningTask {
    private static final Logger LOGGER = LoggerFactory.getLogger("MaidBellowsWorkTask");

    /**
     * 目标风箱位置
     */
    private BlockPos targetPos = null;

    public MaidBellowsWorkTask(double closeEnoughDist) {
        super(ImmutableMap.of(
                InitEntities.TARGET_POS.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, EntityMaid maid) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(posWrapper -> {
            BlockPos pos = posWrapper.currentBlockPosition();
            this.targetPos = pos;
            BlockEntity be = world.getBlockEntity(pos);
            return be instanceof BellowsBlockEntity;
        }).orElse(false);
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTime) {
    }

    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        if (this.targetPos == null) {
            return;
        }

        double distSqr = maid.distanceToSqr(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D);
        if (distSqr > 16.0D) { // 4格范围
            return;
        }

        maid.getLookControl().setLookAt(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D);

        BlockEntity be = world.getBlockEntity(this.targetPos);
        if (!(be instanceof BellowsBlockEntity bellows)) {
            return;
        }

        /**
         * 直接调用风箱的 onRightClick() 方法
         * 这个方法会检查冷却时间（20 tick），并进行拉风箱操作
         */
        bellows.onRightClick();
    }

    @Override
    protected boolean canStillUse(ServerLevel world, EntityMaid maid, long gameTime) {
        if (this.targetPos == null) {
            return false;
        }
        BlockEntity be = world.getBlockEntity(this.targetPos);
        if (!(be instanceof BellowsBlockEntity)) {
            return false;
        }
        double distSqr = maid.distanceToSqr(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D);
        if (distSqr > 16.0D) { // 4格范围
            return false;
        }
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).isPresent();
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTime) {
        this.targetPos = null;
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}
