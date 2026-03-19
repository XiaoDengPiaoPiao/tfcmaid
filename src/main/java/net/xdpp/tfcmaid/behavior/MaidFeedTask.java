package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.xdpp.tfcmaid.config.FeedConfigManager;
import net.xdpp.tfcmaid.util.MaidEquipmentHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆喂养动物的任务
 * <p>
 * 核心功能：找食物 -> 找动物（按优先级排序） -> 喂动物
 * 还有繁殖上限控制、食物保质期检查这些细节
 */
public class MaidFeedTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 20;
    private final float speedModifier;
    private LivingEntity targetAnimal = null;
    private long lastFeedDay = -1;
    private int feedCountToday = 0;

    public MaidFeedTask(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        targetAnimal = null;

        long currentDay = worldIn.getGameTime() / 24000;
        if (currentDay != lastFeedDay) {
            lastFeedDay = currentDay;
            feedCountToday = 0;
        }

        int maxAnimalCount = FeedConfigManager.getMaxAnimalCount();
        double feedPercentage = FeedConfigManager.getFeedPercentageAboveLimit();
        int maxFeedCount = (int) Math.ceil(maxAnimalCount * feedPercentage);

        List<LivingEntity> animalsInRange = getAnimalsInRange(maid);
        int animalCount = (int) animalsInRange.stream()
                .filter(e -> e instanceof TFCAnimalProperties && !(e instanceof OwnableEntity))
                .count();

        if (animalCount >= maxAnimalCount && feedCountToday >= maxFeedCount) {
            return;
        }

        ItemStack food = findAndEquipFood(maid);
        if (food.isEmpty()) {
            return;
        }

        List<LivingEntity> sortedAnimals = sortAnimalsByPriority(animalsInRange, food);

        for (LivingEntity animal : sortedAnimals) {
            if (animalCount >= maxAnimalCount && feedCountToday >= maxFeedCount) {
                break;
            }

            TFCAnimalProperties animalProps = (TFCAnimalProperties) animal;
            if (animalProps.isHungry() && animalProps.isFood(food)) {
                targetAnimal = animal;
                BehaviorUtils.setWalkAndLookTargetMemories(maid, animal, this.speedModifier, 0);
                break;
            }
        }

        if (targetAnimal != null && targetAnimal.closerThan(maid, 2)) {
            TFCAnimalProperties animalProps = (TFCAnimalProperties) targetAnimal;
            ItemStack held = maid.getMainHandItem();

            if (!held.isEmpty() && animalProps.isFood(held)) {
                if (animalProps.isHungry()) {
                    feedAnimal(maid, animalProps, held);
                    
                    if (held.getCount() > 1) {
                        held.shrink(1);
                    } else {
                        maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }

                    feedCountToday++;
                    maid.swing(InteractionHand.MAIN_HAND);
                }
            }
            targetAnimal = null;
        }
    }

    private void feedAnimal(EntityMaid maid, TFCAnimalProperties animalProps, ItemStack food) {
        LivingEntity entity = animalProps.getEntity();
        ServerLevel level = (ServerLevel) entity.level();

        entity.heal(1f);
        if (!level.isClientSide) {
            final long days = animalProps.getCalendar().getTotalDays();
            animalProps.setLastFed(days);
            animalProps.setLastFamiliarityDecay(days);
            
            if (food.hasCraftingRemainingItem()) {
                var backpack = maid.getAvailableBackpackInv();
                ItemHandlerHelper.insertItemStacked(backpack, food.getCraftingRemainingItem().copy(), false);
            }
            
            if (animalProps.getAgeType() == TFCAnimalProperties.Age.CHILD || 
                animalProps.getFamiliarity() < animalProps.getAdultFamiliarityCap()) {
                float familiarity = animalProps.getFamiliarity() + 0.06f;
                if (animalProps.getAgeType() != TFCAnimalProperties.Age.CHILD) {
                    familiarity = Math.min(familiarity, animalProps.getAdultFamiliarityCap());
                }
                animalProps.setFamiliarity(familiarity);
            }
            
            entity.playSound(animalProps.eatingSound(food), 1f, 1f);
        }
    }

    private List<LivingEntity> getAnimalsInRange(EntityMaid maid) {
        List<LivingEntity> animals = new ArrayList<>();
        getEntities(maid).findAll(e -> maid.isWithinRestriction(e.blockPosition()) && 
                e.isAlive() && 
                e instanceof TFCAnimalProperties && 
                !(e instanceof OwnableEntity) && 
                maid.canPathReach(e))
                .forEach(animals::add);
        return animals;
    }

    private List<LivingEntity> sortAnimalsByPriority(List<LivingEntity> animals, ItemStack food) {
        animals.sort((a, b) -> {
            TFCAnimalProperties aProps = (TFCAnimalProperties) a;
            TFCAnimalProperties bProps = (TFCAnimalProperties) b;
            int aPriority = getAgePriority(aProps);
            int bPriority = getAgePriority(bProps);
            
            if (aPriority != bPriority) {
                return Integer.compare(aPriority, bPriority);
            }
            
            boolean aHungry = aProps.isHungry() && aProps.isFood(food);
            boolean bHungry = bProps.isHungry() && bProps.isFood(food);
            return Boolean.compare(bHungry, aHungry);
        });
        return animals;
    }

    private int getAgePriority(TFCAnimalProperties animal) {
        TFCAnimalProperties.Age age = animal.getAgeType();
        return switch (age) {
            case CHILD -> 0;
            case ADULT -> 1;
            case OLD -> 2;
        };
    }

    private ItemStack findAndEquipFood(EntityMaid maid) {
        if (MaidEquipmentHelper.findAndEquipItem(maid, this::isValidFood)) {
            return maid.getMainHandItem();
        }
        return ItemStack.EMPTY;
    }

    private boolean isValidFood(ItemStack stack) {
        return !FoodCapability.isRotten(stack);
    }

    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
    }
}
