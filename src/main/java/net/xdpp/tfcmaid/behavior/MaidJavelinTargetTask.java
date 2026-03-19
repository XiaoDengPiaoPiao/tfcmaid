package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitAttribute;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.dries007.tfc.common.items.JavelinItem;

import java.util.Optional;

public class MaidJavelinTargetTask extends Behavior<EntityMaid> {
    private final int attackCooldown = 20;
    private int attackTime = -1;
    private int seeTime;

    public MaidJavelinTargetTask() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        Optional<LivingEntity> memory = owner.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        if (memory.isPresent()) {
            LivingEntity target = memory.get();
            return hasJavelin(owner) && owner.canSee(target);
        }
        return false;
    }

    @Override
    protected boolean canStillUse(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        return entityIn.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && this.checkExtraStartConditions(worldIn, entityIn);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        entityIn.setSwingingArms(true);
    }

    @Override
    protected void tick(ServerLevel worldIn, EntityMaid owner, long gameTime) {
        owner.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent((target) -> {
            owner.getLookControl().setLookAt(target.getX(), target.getY(), target.getZ());
            boolean canSee = owner.canSee(target);
            boolean seeTimeMoreThanZero = this.seeTime > 0;

            if (canSee != seeTimeMoreThanZero) {
                this.seeTime = 0;
            }
            if (canSee) {
                ++this.seeTime;
            } else {
                --this.seeTime;
            }

            if (owner.isUsingItem()) {
                if (!canSee && this.seeTime < -60) {
                    owner.stopUsingItem();
                } else if (canSee) {
                    int ticksUsingItem = owner.getTicksUsingItem();
                    boolean tooClose = owner.closerThan(target, 6);
                    boolean inSafeArea = !tooClose;
                    if (ticksUsingItem >= 30 && inSafeArea) {
                        owner.stopUsingItem();
                        owner.performRangedAttack(target, 0);
                        AttributeInstance attributeInstance = owner.getAttribute(InitAttribute.MAID_TRIDENT_COOLDOWN.get());
                        if (attributeInstance != null) {
                            this.attackTime = (int) attributeInstance.getValue();
                        } else {
                            this.attackTime = this.attackCooldown;
                        }
                    }
                }
            } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
                owner.startUsingItem(InteractionHand.MAIN_HAND);
            }
        });
    }

    @Override
    protected void stop(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        this.seeTime = 0;
        this.attackTime = -1;
        entityIn.stopUsingItem();
    }

    private boolean hasJavelin(EntityMaid maid) {
        return maid.getMainHandItem().getItem() instanceof JavelinItem;
    }
}
