package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.dries007.tfc.common.blockentities.BellowsBlockEntity;
import net.minecraft.server.level.ServerLevel;

/**
 * 风箱工作任务类
 * 继承自 AbstractBlockEntityWorkTask，实现拉风箱操作的完整流程
 * 工作流程：
 * 1. 走到风箱4格范围内
 * 2. 检查风箱是否可以拉风箱（是否连接了曲轴，或者是否在冷却时间）
 * 3. 调用风箱的 onRightClick() 方法进行拉风箱
 * 功能特点：
 * - 每20 tick（1秒）可以拉风箱一次
 * - 距离检查：只有在风箱4格范围内才能工作
 */
public class MaidBellowsWorkTask extends AbstractBlockEntityWorkTask<BellowsBlockEntity> {

    public MaidBellowsWorkTask(double closeEnoughDist) {
        super();
    }

    @Override
    protected Class<BellowsBlockEntity> getBlockEntityClass() {
        return BellowsBlockEntity.class;
    }

    @Override
    protected void tickWork(ServerLevel world, EntityMaid maid, long gameTime, BellowsBlockEntity bellows) {
        /**
         * 直接调用风箱的 onRightClick() 方法
         * 这个方法会检查冷却时间（20 tick），并进行拉风箱操作
         */
        bellows.onRightClick();
    }
}
