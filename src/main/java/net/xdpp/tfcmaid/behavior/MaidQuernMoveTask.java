package net.xdpp.tfcmaid.behavior;

import net.dries007.tfc.common.blockentities.QuernBlockEntity;

/**
 * 推磨寻路任务
 * 继承自 AbstractBlockEntityMoveTask，实现寻找并移动到推磨
 * 功能：
 * 1. 搜索附近的推磨方块实体（QuernBlockEntity）
 * 2. 控制女仆移动到推磨旁边
 * 3. 设置垂直搜索范围为5，确保能找到不同高度的推磨
 */
public class MaidQuernMoveTask extends AbstractBlockEntityMoveTask<QuernBlockEntity> {

    /**
     * 构造函数
     *
     * @param speed 移动速度
     */
    public MaidQuernMoveTask(float speed) {
        super(speed);
    }

    @Override
    protected Class<QuernBlockEntity> getBlockEntityClass() {
        return QuernBlockEntity.class;
    }
}
