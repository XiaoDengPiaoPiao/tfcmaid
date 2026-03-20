package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidArriveAtBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.xdpp.tfcmaid.Tfcmaid;
import net.xdpp.tfcmaid.behavior.MaidLoomMoveTask;
import net.xdpp.tfcmaid.behavior.MaidLoomWorkTask;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * TFC织姬任务类
 * 实现逻辑：女仆使用TFC织机进行织布操作
 * 使用条件：
 * 1. 主手放置织机成果物品（如tfc:silk_cloth）
 * 2. 副手放置木棍
 * 工作流程：
 * - 寻找附近的织机
 * - 走到织机旁边
 * - 查询与主手物品对应的配方
 * - 检查织机已有原料，从背包补充不足的部分
 * - 操作织机推进进度
 * - 完成后收取成品到背包
 * - 重复直到原料耗尽
 */
public class TaskTFCLoom implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_loom");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("tfc:wood/planks/acacia_loom"))
                .map(ItemStack::new)
                .orElse(Items.SPIDER_EYE.getDefaultInstance());
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        MaidLoomMoveTask moveTask = new MaidLoomMoveTask(0.6f);
        MaidLoomWorkTask workTask = new MaidLoomWorkTask(2.0);
        return Lists.newArrayList(
                Pair.of(5, moveTask),
                Pair.of(6, workTask)
        );
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(
                Pair.of("has_loom_result", this::hasLoomResult),
                Pair.of("has_stick_in_offhand", this::hasStickInOffhand)
        );
    }

    /**
     * 检查女仆主手是否放置了织机成果物品
     *
     * @param maid 女仆实体
     * @return true表示主手有物品
     */
    private boolean hasLoomResult(EntityMaid maid) {
        return !maid.getMainHandItem().isEmpty();
    }

    /**
     * 检查女仆副手是否放置了木棍
     *
     * @param maid 女仆实体
     * @return true表示副手有木棍
     */
    private boolean hasStickInOffhand(EntityMaid maid) {
        ItemStack offhand = maid.getOffhandItem();
        return !offhand.isEmpty() && offhand.is(net.minecraft.world.item.Items.STICK);
    }

    @Override
    public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) {
        if (!maid.getMainHandItem().isEmpty() && hasStickInOffhand(maid)) {
            return FunctionCallSwitchResult.NO_CHANGE;
        }
        return FunctionCallSwitchResult.MISSING_REQUIRED_ITEM;
    }

    @Override
    public boolean workPointTask(EntityMaid maid) {
        return true;
    }

    public String getMaidActionSummary() {
        return "Weave cloth using a TFC loom.";
    }
}
