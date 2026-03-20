package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.dries007.tfc.common.blockentities.BellowsBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 风箱寻路任务类
 * 继承自 MaidMoveToBlockTask，用于让女仆寻找附近的风箱
 */
public class MaidBellowsMoveTask extends MaidMoveToBlockTask {
    public MaidBellowsMoveTask(float movementSpeed) {
        super(movementSpeed, 5);
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel worldIn, EntityMaid entityIn, BlockPos pos) {
        BlockEntity be = worldIn.getBlockEntity(pos);
        return be instanceof BellowsBlockEntity;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        searchForDestination(worldIn, entityIn);
    }
}
