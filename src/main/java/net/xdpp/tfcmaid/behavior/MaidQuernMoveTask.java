package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.dries007.tfc.common.blockentities.QuernBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 推磨寻路任务
 * 继承自 MaidMoveToBlockTask，实现寻找并移动到推磨
 * 功能：
 * 1. 搜索附近的推磨方块实体（QuernBlockEntity）
 * 2. 控制女仆移动到推磨旁边
 * 3. 设置垂直搜索范围为5，确保能找到不同高度的推磨
 */
public class MaidQuernMoveTask extends MaidMoveToBlockTask {

    /**
     * 构造函数
     *
     * @param speed 移动速度
     */
    public MaidQuernMoveTask(float speed) {
        super(speed, 5);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        searchForDestination(worldIn, maid);
    }

    /**
     * 判断是否应该移动到指定位置
     * 检查该位置的方块实体是否为推磨方块实体
     *
     * @param worldIn 世界
     * @param entityIn 女仆实体
     * @param pos 要检查的位置
     * @return true表示是推磨，应该移动到这里
     */
    @Override
    protected boolean shouldMoveTo(ServerLevel worldIn, EntityMaid entityIn, BlockPos pos) {
        BlockEntity be = worldIn.getBlockEntity(pos);
        return be instanceof QuernBlockEntity;
    }
}
