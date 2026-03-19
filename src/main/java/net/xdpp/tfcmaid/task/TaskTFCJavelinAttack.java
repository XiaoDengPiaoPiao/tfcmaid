package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidRangedWalkToTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.dries007.tfc.client.TFCSounds;
import net.dries007.tfc.common.blocks.rock.RockCategory;
import net.dries007.tfc.common.entities.misc.ThrownJavelin;
import net.dries007.tfc.common.items.JavelinItem;
import net.dries007.tfc.common.items.TFCItems;
import net.xdpp.tfcmaid.Tfcmaid;
import net.xdpp.tfcmaid.behavior.MaidAttackJavelinTask;
import net.xdpp.tfcmaid.behavior.MaidJavelinTargetTask;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

public class TaskTFCJavelinAttack implements IRangedAttackTask {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_javelin_attack");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
    //小心地雷！！！
    //获取任务图标
    //有人反馈说在整合包使用报错，获取不到tfc原版的图标，所以添加catch来处理，出现异常时返回铁剑(因为一个附属mod对tfc的图标机制进行了改动才导致了这个情况)
    @Override
    public ItemStack getIcon() {
        try {
            return TFCItems.ROCK_TOOLS.get(RockCategory.IGNEOUS_EXTRUSIVE).get(RockCategory.ItemType.JAVELIN).get().getDefaultInstance();
        } catch (Exception e) {
            return net.minecraft.world.item.Items.IRON_SWORD.getDefaultInstance();
        }
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.attackSound(maid, InitSounds.MAID_RANGE_ATTACK.get(), 0.5f);
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> supplementedTask = StartAttacking.create(this::hasJavelin, IRangedAttackTask::findFirstValidAttackTarget);
        BehaviorControl<EntityMaid> findTargetTask = StopAttackingIfTargetInvalid.create((target) -> !hasJavelin(maid) || farAway(target, maid));
        BehaviorControl<EntityMaid> moveToTargetTask = MaidRangedWalkToTarget.create(0.6f);
        BehaviorControl<EntityMaid> maidAttackStrafingTask = new MaidAttackJavelinTask();
        BehaviorControl<EntityMaid> shootTargetTask = new MaidJavelinTargetTask();

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, moveToTargetTask),
                Pair.of(5, maidAttackStrafingTask),
                Pair.of(5, shootTargetTask)
        );
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> supplementedTask = StartAttacking.create(this::hasJavelin, IRangedAttackTask::findFirstValidAttackTarget);
        BehaviorControl<EntityMaid> findTargetTask = StopAttackingIfTargetInvalid.create((target) -> !hasJavelin(maid) || farAway(target, maid));
        BehaviorControl<EntityMaid> shootTargetTask = new MaidJavelinTargetTask();

        return Lists.newArrayList(
                Pair.of(5, supplementedTask),
                Pair.of(5, findTargetTask),
                Pair.of(5, shootTargetTask)
        );
    }

    @Override
    public boolean canSee(EntityMaid maid, LivingEntity target) {
        return IRangedAttackTask.targetConditionsTest(maid, target, MaidConfig.TRIDENT_RANGE);
    }

    @Override
    public AABB searchDimension(EntityMaid maid) {
        if (hasJavelin(maid)) {
            float searchRange = this.searchRadius(maid);
            if (maid.hasRestriction()) {
                return new AABB(maid.getRestrictCenter()).inflate(searchRange);
            } else {
                return maid.getBoundingBox().inflate(searchRange);
            }
        }
        return IRangedAttackTask.super.searchDimension(maid);
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        return MaidConfig.TRIDENT_RANGE.get();
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
        ItemStack javelinItem = shooter.getMainHandItem().copy();

        ThrownJavelin thrownJavelin = new ThrownJavelin(shooter.level(), shooter, javelinItem);
        double x = target.getX() - shooter.getX();
        double y = target.getEyeY() - shooter.getEyeY();
        double z = target.getZ() - shooter.getZ();

        float distance = shooter.distanceTo(target);
        float velocity = Mth.clamp(distance / 10f, 1.6f, 3.2f);
        float inaccuracy = 1 - Mth.clamp(distance / 100f, 0, 0.9f);

        thrownJavelin.setNoGravity(true);
        thrownJavelin.shoot(x, y, z, velocity, inaccuracy);
        thrownJavelin.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        shooter.getMainHandItem().hurtAndBreak(1, shooter, maid -> maid.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        shooter.level().addFreshEntity(thrownJavelin);
        shooter.playSound(TFCSounds.JAVELIN_THROWN.get(), 1.0f, 1.0f);
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("has_javelin", this::hasJavelin));
    }

    @Override
    public boolean isWeapon(EntityMaid maid, ItemStack stack) {
        return stack.getItem() instanceof JavelinItem;
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > this.searchRadius(maid);
    }

    private boolean hasJavelin(EntityMaid maid) {
        return maid.getMainHandItem().getItem() instanceof JavelinItem;
    }
}
