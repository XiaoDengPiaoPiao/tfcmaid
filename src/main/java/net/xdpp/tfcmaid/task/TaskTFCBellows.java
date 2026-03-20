package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
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
import net.xdpp.tfcmaid.behavior.MaidBellowsMoveTask;
import net.xdpp.tfcmaid.behavior.MaidBellowsWorkTask;

import javax.annotation.Nullable;
import java.util.List;

/**
 * TFC拉风箱任务类
 * 实现逻辑：女仆使用TFC风箱进行拉风箱操作
 * 工作流程：
 * - 寻找附近的风箱
 * - 走到风箱旁边
 * - 右键风箱进行拉风箱操作
 * - 每20 tick（1秒）可以拉风箱一次
 */
public class TaskTFCBellows implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_bellows");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("tfc:bellows"))
                .map(ItemStack::new)
                .orElse(Items.BLACKSTONE.getDefaultInstance());
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        MaidBellowsMoveTask moveTask = new MaidBellowsMoveTask(0.6f);
        MaidBellowsWorkTask workTask = new MaidBellowsWorkTask(2.0);
        return Lists.newArrayList(
                Pair.of(5, moveTask),
                Pair.of(6, workTask)
        );
    }

    @Override
    public List<Pair<String, java.util.function.Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList();
    }

    @Override
    public boolean workPointTask(EntityMaid maid) {
        return true;
    }

    public String getMaidActionSummary() {
        return "Blow air using a TFC bellows.";
    }
}
