package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

import java.util.Map;

/**
 * 女仆长时间运行的行为任务基类
 * 适用于需要持续 tick 更新的任务，比如筛矿、钓鱼等
 * 与 MaidCheckRateTask 不同，这个类不会限制任务的检查频率
 */
public abstract class MaidLongRunningTask extends Behavior<EntityMaid> {
    private static final int DEFAULT_DURATION = 10000;

    protected MaidLongRunningTask(Map<MemoryModuleType<?>, MemoryStatus> requiredMemoryStateIn) {
        super(requiredMemoryStateIn, DEFAULT_DURATION);
    }

    protected MaidLongRunningTask(Map<MemoryModuleType<?>, MemoryStatus> requiredMemoryStateIn, int duration) {
        super(requiredMemoryStateIn, duration);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        return true;
    }
}
