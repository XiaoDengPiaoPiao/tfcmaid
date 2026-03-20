package net.xdpp.tfcmaid.behavior;

import net.dries007.tfc.common.blockentities.BellowsBlockEntity;

/**
 * 风箱寻路任务类
 * 继承自 AbstractBlockEntityMoveTask，用于让女仆寻找附近的风箱
 */
public class MaidBellowsMoveTask extends AbstractBlockEntityMoveTask<BellowsBlockEntity> {
    public MaidBellowsMoveTask(float movementSpeed) {
        super(movementSpeed);
    }

    @Override
    protected Class<BellowsBlockEntity> getBlockEntityClass() {
        return BellowsBlockEntity.class;
    }
}
