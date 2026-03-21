package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.dries007.tfc.common.blockentities.LoomBlockEntity;
import net.minecraft.server.level.ServerLevel;

/**
 * 织机寻路任务
 * 继承自 AbstractBlockEntityMoveTask，实现寻找并移动到织机
 * 功能：
 * 1. 搜索附近的织机方块实体（LoomBlockEntity）
 * 2. 控制女仆移动到织机旁边
 * 3. 设置垂直搜索范围为5，确保能找到不同高度的织机
 * 4. 只有当女仆主手有物品时才会触发寻路
 */
public class MaidLoomMoveTask extends AbstractBlockEntityMoveTask<LoomBlockEntity> {

    /**
     * 构造函数
     *
     * @param speed 移动速度
     */
    public MaidLoomMoveTask(float speed) {
        super(speed);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        if (!maid.getMainHandItem().isEmpty()) {
            super.start(worldIn, maid, gameTimeIn);
        }
    }

    @Override
    protected Class<LoomBlockEntity> getBlockEntityClass() {
        return LoomBlockEntity.class;
    }
}
