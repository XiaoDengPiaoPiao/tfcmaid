package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.dries007.tfc.common.entities.Pluckable;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

public class MaidPluckFeatherTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 12;
    private final float speedModifier;
    private LivingEntity pluckableEntity = null;

    public MaidPluckFeatherTask(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        pluckableEntity = null;

        this.getEntities(maid)
                .find(e -> maid.isWithinRestriction(e.blockPosition()))
                .filter(LivingEntity::isAlive)
                .filter(e -> e instanceof Pluckable)
                .filter(e -> e instanceof TFCAnimalProperties && ((TFCAnimalProperties) e).getAgeType() == TFCAnimalProperties.Age.ADULT)
                .filter(maid::canPathReach)
                .findFirst()
                .ifPresent(e -> {
                    pluckableEntity = e;
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, e, this.speedModifier, 0);
                });

        if (pluckableEntity != null && pluckableEntity.closerThan(maid, 2)) {
            Pluckable pluckable = (Pluckable) pluckableEntity;
            if (pluckable.getLastPluckedTick() + Pluckable.PLUCKING_COOLDOWN <= worldIn.getGameTime()) {
                if (pluckableEntity.getHealth() / pluckableEntity.getMaxHealth() > 0.15001f) {
                    TFCAnimalProperties properties = (TFCAnimalProperties) pluckableEntity;
                    if (properties.getUses() < properties.getUsesToElderly()) {
                        List<ItemStack> drops = pluck(maid, pluckable, pluckableEntity);
                        drops.forEach(stack -> {
                            ItemEntity itemEntity = pluckableEntity.spawnAtLocation(stack, 1.0F);
                            if (itemEntity != null) {
                                itemEntity.setDeltaMovement(itemEntity.getDeltaMovement().add(
                                        (maid.getRandom().nextFloat() - maid.getRandom().nextFloat()) * 0.1F,
                                        maid.getRandom().nextFloat() * 0.05F,
                                        (maid.getRandom().nextFloat() - maid.getRandom().nextFloat()) * 0.1F));
                            }
                        });
                        maid.swing(InteractionHand.MAIN_HAND);
                        pluckable.setLastPluckedTick(worldIn.getGameTime());
                        properties.addUses(1);
                    }
                }
            }
            pluckableEntity = null;
        }
    }

    private List<ItemStack> pluck(EntityMaid maid, Pluckable pluckable, LivingEntity entity) {
        int count = maid.getRandom().nextInt(3) + 1;
        ItemStack feather = new ItemStack(net.minecraft.world.item.Items.FEATHER, count);
        entity.hurt(maid.damageSources().generic(), entity.getMaxHealth() * 0.15f);
        return List.of(feather);
    }

    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
    }
}
