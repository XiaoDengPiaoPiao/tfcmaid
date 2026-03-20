package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.dries007.tfc.common.blockentities.LoomBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 织机寻路任务
 * 继承自 MaidMoveToBlockTask，实现寻找并移动到织机
 * 功能：
 * 1. 搜索附近的织机方块实体（LoomBlockEntity）
 * 2. 控制女仆移动到织机旁边
 * 3. 设置垂直搜索范围为5，确保能找到不同高度的织机
 */
public class MaidLoomMoveTask extends MaidMoveToBlockTask {

    /**
     * 构造函数
     *
     * @param speed 移动速度
     */
    public MaidLoomMoveTask(float speed) {
        super(speed, 5);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        if (!maid.getMainHandItem().isEmpty()) {
            searchForDestination(worldIn, maid);
        }
    }

    /**
     * 判断是否应该移动到指定位置
     * 检查该位置的方块实体是否为织机方块实体
     *
     * @param worldIn 世界
     * @param entityIn 女仆实体
     * @param pos 要检查的位置
     * @return true表示是织机，应该移动到这里
     */
    @Override
    protected boolean shouldMoveTo(ServerLevel worldIn, EntityMaid entityIn, BlockPos pos) {
        BlockEntity be = worldIn.getBlockEntity(pos);
        return be instanceof LoomBlockEntity;
    }
}
