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
import net.xdpp.tfcmaid.behavior.MaidQuernMoveTask;
import net.xdpp.tfcmaid.behavior.MaidQuernWorkTask;

import javax.annotation.Nullable;
import java.util.List;

/**
 * TFC推磨任务类
 * 实现逻辑：女仆使用TFC推磨进行推磨操作
 * 工作流程：
 * - 寻找附近的推磨
 * - 走到推磨旁边
 * - 检查推磨是否有手石，没有的话从背包找一个
 * - 检查输出槽，有物品就取出来
 * - 按顺序找可以磨的物品：主手 → 副手 → 背包
 * - 将可磨物品放到推磨输入槽，开始推磨
 * - 等待推磨完成，收集输出
 * - 重复直到没有可磨的物品
 */
public class TaskTFCQuern implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_quern");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("tfc:quern"))
                .map(ItemStack::new)
                .orElse(Items.COBBLESTONE.getDefaultInstance());
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        MaidQuernMoveTask moveTask = new MaidQuernMoveTask(0.6f);
        MaidQuernWorkTask workTask = new MaidQuernWorkTask(2.0);
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
        return "Grind items using a TFC quern.";
    }
}
