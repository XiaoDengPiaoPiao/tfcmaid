package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 方块实体工作任务抽象基类
 * 复用工作任务的公共逻辑
 * 功能：
 * - 提供统一的 targetPos 字段存储目标位置
 * - 提供统一的距离检查（4格范围，距离平方 ≤ 16）
 * - 提供统一的 checkExtraStartConditions() 方法，检查 TARGET_POS 内存和方块实体类型
 * - 提供统一的 canStillUse() 方法，检查距离和 TARGET_POS 内存
 * - 提供统一的 stop() 方法，清除 targetPos 和相关内存
 * - 提供统一的 lookAtTarget() 方法，让女仆看向目标方块
 * - 提供 getBlockEntityAtTarget() 方法获取目标位置的方块实体
 * - 子类只需实现 tickWork() 方法进行具体工作
 *
 * @param <T> 方块实体类型
 */
public abstract class AbstractBlockEntityWorkTask<T extends BlockEntity> extends MaidLongRunningTask {

    /**
     * 目标方块位置
     */
    protected BlockPos targetPos = null;

    public AbstractBlockEntityWorkTask() {
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
            return getBlockEntityClass().isInstance(be);
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

        if (!isInRange(maid)) {
            return;
        }

        lookAtTarget(maid);

        T blockEntity = getBlockEntityAtTarget(world);
        if (blockEntity != null) {
            tickWork(world, maid, gameTime, blockEntity);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel world, EntityMaid maid, long gameTime) {
        if (this.targetPos == null) {
            return false;
        }
        BlockEntity be = world.getBlockEntity(this.targetPos);
        if (!getBlockEntityClass().isInstance(be)) {
            return false;
        }
        if (!isInRange(maid)) {
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

    /**
     * 检查女仆是否在目标位置的4格范围内
     *
     * @param maid 女仆实体
     * @return true表示在范围内
     */
    protected boolean isInRange(EntityMaid maid) {
        double distSqr = maid.distanceToSqr(
                this.targetPos.getX() + 0.5D,
                this.targetPos.getY() + 0.5D,
                this.targetPos.getZ() + 0.5D
        );
        return distSqr <= 16.0D;
    }

    /**
     * 让女仆看向目标位置的方块
     *
     * @param maid 女仆实体
     */
    protected void lookAtTarget(EntityMaid maid) {
        maid.getLookControl().setLookAt(
                this.targetPos.getX() + 0.5D,
                this.targetPos.getY() + 0.5D,
                this.targetPos.getZ() + 0.5D
        );
    }

    /**
     * 获取目标位置的方块实体
     *
     * @param world 世界
     * @return 目标方块实体，类型为 T，找不到返回 null
     */
    @SuppressWarnings("unchecked")
    protected T getBlockEntityAtTarget(ServerLevel world) {
        BlockEntity be = world.getBlockEntity(this.targetPos);
        if (getBlockEntityClass().isInstance(be)) {
            return (T) be;
        }
        return null;
    }

    /**
     * 获取方块实体类型
     *
     * @return 方块实体 Class 对象
     */
    protected abstract Class<T> getBlockEntityClass();

    /**
     * 子类实现此方法进行具体工作
     *
     * @param world       世界
     * @param maid        女仆实体
     * @param gameTime    游戏时间
     * @param blockEntity 目标方块实体
     */
    protected abstract void tickWork(ServerLevel world, EntityMaid maid, long gameTime, T blockEntity);
}
