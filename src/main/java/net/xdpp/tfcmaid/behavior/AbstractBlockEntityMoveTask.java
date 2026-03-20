package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 方块实体寻路任务抽象基类
 * 复用寻路任务的公共逻辑
 * 功能：
 * - 提供统一的构造函数（movementSpeed 和 verticalSearchRange=5）
 * - 提供统一的 start() 方法，调用 searchForDestination()
 * - 子类只需实现 getBlockEntityClass() 返回需要寻找的方块实体类型
 *
 * @param <T> 方块实体类型
 */
public abstract class AbstractBlockEntityMoveTask<T extends BlockEntity> extends MaidMoveToBlockTask {

    public AbstractBlockEntityMoveTask(float movementSpeed) {
        super(movementSpeed, 5);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        searchForDestination(worldIn, maid);
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel worldIn, EntityMaid entityIn, BlockPos pos) {
        BlockEntity be = worldIn.getBlockEntity(pos);
        return getBlockEntityClass().isInstance(be);
    }

    /**
     * 获取需要寻找的方块实体类型
     *
     * @return 方块实体 Class 对象
     */
    protected abstract Class<T> getBlockEntityClass();
}
