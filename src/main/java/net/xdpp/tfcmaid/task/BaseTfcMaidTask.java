package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * TFC 女仆任务基类
 * <p>
 * 封装了 IMaidTask 接口的通用实现，包括任务 UID、图标、声音、优先级等
 */
public abstract class BaseTfcMaidTask implements IMaidTask {
    protected final ResourceLocation uid;
    protected final ItemStack icon;
    protected final SoundEvent ambientSound;
    protected final int priority;
    protected final BehaviorControl<? super EntityMaid> task;
    protected final Predicate<EntityMaid> condition;
    protected final String conditionKey;

    protected BaseTfcMaidTask(ResourceLocation uid, ItemStack icon, 
            SoundEvent ambientSound, int priority, 
            BehaviorControl<? super EntityMaid> task,
            Predicate<EntityMaid> condition, String conditionKey) {
        this.uid = uid;
        this.icon = icon;
        this.ambientSound = ambientSound;
        this.priority = priority;
        this.task = task;
        this.condition = condition;
        this.conditionKey = conditionKey;
    }

    @Override
    public ResourceLocation getUid() {
        return uid;
    }

    @Override
    public ItemStack getIcon() {
        return icon;
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, ambientSound, 0.5f);
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return Lists.newArrayList(Pair.of(priority, task));
    }

    @Override
    public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) {
        if (condition.test(maid)) {
            return FunctionCallSwitchResult.NO_CHANGE;
        }
        return FunctionCallSwitchResult.MISSING_REQUIRED_ITEM;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        if (conditionKey != null && condition != null) {
            return Lists.newArrayList(Pair.of(conditionKey, condition));
        }
        return Lists.newArrayList();
    }
}
