package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.xdpp.tfcmaid.config.FeedConfigManager;
import net.xdpp.tfcmaid.util.WirelessIOHelper;

import java.util.*;

import static com.github.tartaricacid.touhoulittlemaid.util.BytesBooleansConvert.bytes2Booleans;

// 女仆喂养动物的任务
// 核心功能：找食物 -> 找动物（按优先级排序） -> 喂动物
// 还有繁殖上限控制、食物保质期检查、隙间支持这些细节
public class MaidFeedTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 20; // 检查间隔，20tick也就是1秒
    private final float speedModifier;
    private LivingEntity targetAnimal = null;
    private long lastFeedDay = -1; // 上次喂养的天，用来控制每天喂养次数
    private int feedCountToday = 0; // 今天已经喂了多少只

    public MaidFeedTask(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        targetAnimal = null;

        // 新的一天了，重置喂养计数
        long currentDay = worldIn.getGameTime() / 24000;
        if (currentDay != lastFeedDay) {
            lastFeedDay = currentDay;
            feedCountToday = 0;
        }

        // 从配置里读上限和超上限后的喂养百分比
        int maxAnimalCount = FeedConfigManager.getMaxAnimalCount();
        double feedPercentage = FeedConfigManager.getFeedPercentageAboveLimit();
        int maxFeedCount = (int) Math.ceil(maxAnimalCount * feedPercentage);

        // 统计工作范围内有多少动物
        List<LivingEntity> animalsInRange = getAnimalsInRange(maid);
        int animalCount = (int) animalsInRange.stream()
                .filter(e -> e instanceof TFCAnimalProperties && !(e instanceof OwnableEntity))
                .count();

        // 动物超上限了，今天也喂够了，停止工作
        if (animalCount >= maxAnimalCount && feedCountToday >= maxFeedCount) {
            return;
        }

        // 找食物，找不到就歇菜
        ItemStack food = findAndEquipFood(maid);
        if (food.isEmpty()) {
            return;
        }

        // 按优先级给动物排序：未成年 > 成年 > 衰老，同年龄的饿的优先
        List<LivingEntity> sortedAnimals = sortAnimalsByPriority(animalsInRange, food);

        // 找第一个能喂的动物
        for (LivingEntity animal : sortedAnimals) {
            if (animalCount >= maxAnimalCount && feedCountToday >= maxFeedCount) {
                break; // 超上限且喂够了，停止
            }

            TFCAnimalProperties animalProps = (TFCAnimalProperties) animal;
            if (animalProps.isHungry() && animalProps.isFood(food)) {
                targetAnimal = animal;
                BehaviorUtils.setWalkAndLookTargetMemories(maid, animal, this.speedModifier, 0);
                break;
            }
        }

        // 走到了就开始喂
        if (targetAnimal != null && targetAnimal.closerThan(maid, 2)) {
            TFCAnimalProperties animalProps = (TFCAnimalProperties) targetAnimal;
            ItemStack held = maid.getMainHandItem();

            if (!held.isEmpty() && animalProps.isFood(held)) {
                if (animalProps.isHungry()) {
                    feedAnimal(maid, animalProps, held);
                    
                    // 消耗食物，多个就减1，单个就清空主手
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

    // 核心喂养逻辑，手动实现的，因为TFC的feed方法需要Player
    private void feedAnimal(EntityMaid maid, TFCAnimalProperties animalProps, ItemStack food) {
        LivingEntity entity = animalProps.getEntity();
        ServerLevel level = (ServerLevel) entity.level();

        entity.heal(1f); // 喂完加点血，模拟原版效果
        if (!level.isClientSide) {
            final long days = animalProps.getCalendar().getTotalDays();
            animalProps.setLastFed(days); // 记录最后喂养时间
            animalProps.setLastFamiliarityDecay(days); // 重置熟悉度衰减时间
            
            // 处理食物剩余物（比如碗）
            if (food.hasCraftingRemainingItem()) {
                var backpack = maid.getAvailableBackpackInv();
                ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, food.getCraftingRemainingItem().copy(), false);
                if (!remaining.isEmpty()) {
                    remaining = WirelessIOHelper.tryInsertToChest(maid, remaining); // 背包满了塞隙间
                }
            }
            
            // 增加熟悉度，但成年的不能超过上限
            if (animalProps.getAgeType() == TFCAnimalProperties.Age.CHILD || 
                animalProps.getFamiliarity() < animalProps.getAdultFamiliarityCap()) {
                float familiarity = animalProps.getFamiliarity() + 0.06f;
                if (animalProps.getAgeType() != TFCAnimalProperties.Age.CHILD) {
                    familiarity = Math.min(familiarity, animalProps.getAdultFamiliarityCap());
                }
                animalProps.setFamiliarity(familiarity);
            }
            
            // 播放打嗝音效
            entity.playSound(animalProps.eatingSound(food), 1f, 1f);
        }
    }

    // 获取工作范围内所有满足条件的动物
    private List<LivingEntity> getAnimalsInRange(EntityMaid maid) {
        List<LivingEntity> animals = new ArrayList<>();
        getEntities(maid).findAll(e -> maid.isWithinRestriction(e.blockPosition()) && 
                e.isAlive() && 
                e instanceof TFCAnimalProperties && 
                !(e instanceof OwnableEntity) && // 不是宠物（马、狗等）
                maid.canPathReach(e))
                .forEach(animals::add);
        return animals;
    }

    // 按优先级给动物排序：
    // 1. 先按年龄：未成年(0) > 成年(1) > 衰老(2)
    // 2. 同年龄的话，饿的优先
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
            return Boolean.compare(bHungry, aHungry); // true在前，也就是饿的在前
        });
        return animals;
    }

    // 获取年龄优先级，数字越小优先级越高
    private int getAgePriority(TFCAnimalProperties animal) {
        TFCAnimalProperties.Age age = animal.getAgeType();
        return switch (age) {
            case CHILD -> 0;  // 未成年优先级最高
            case ADULT -> 1;  // 成年次之
            case OLD -> 2;    // 衰老最后
        };
    }

    // 找食物并装备到主手
    // 查找顺序：主手 -> 背包 -> 箱子
    private ItemStack findAndEquipFood(EntityMaid maid) {
        ItemStack mainHand = maid.getMainHandItem();
        
        // 先看主手有没有能用的食物
        if (!mainHand.isEmpty() && isValidFood(mainHand)) {
            return mainHand;
        }

        // 主手没有，翻背包
        var backpack = maid.getAvailableBackpackInv();
        for (int i = 0; i < backpack.getSlots(); i++) {
            ItemStack stack = backpack.getStackInSlot(i);
            if (!stack.isEmpty() && isValidFood(stack)) {
                ItemStack extracted = backpack.extractItem(i, 1, false);
                if (!mainHand.isEmpty()) {
                    // 主手有东西，先塞回背包
                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, mainHand, false);
                    if (!remaining.isEmpty()) {
                        remaining = WirelessIOHelper.tryInsertToChest(maid, remaining);
                    }
                    if (!remaining.isEmpty()) {
                        // 塞不下，还原状态
                        backpack.insertItem(i, extracted, false);
                        return ItemStack.EMPTY;
                    }
                }
                maid.setItemInHand(InteractionHand.MAIN_HAND, extracted);
                return extracted;
            }
        }

        // 背包也没有，试试隙间
        ItemStack wirelessIO = WirelessIOHelper.getWirelessIOBauble(maid);
        if (!wirelessIO.isEmpty()) {
            ItemStack foodFromChest = findFoodInWirelessIOChest(maid, wirelessIO, mainHand);
            if (!foodFromChest.isEmpty()) {
                return foodFromChest;
            }
        }

        // 哪都找不到
        return ItemStack.EMPTY;
    }

    // 检查食物是否有效：只要没腐烂就行
    private boolean isValidFood(ItemStack stack) {
        return !FoodCapability.isRotten(stack);
    }

    // 从绑定的箱子里找食物
    private ItemStack findFoodInWirelessIOChest(EntityMaid maid, ItemStack wirelessIO, ItemStack mainHand) {
        var bindingPos = ItemWirelessIO.getBindingPos(wirelessIO);
        if (bindingPos == null || !maid.isWithinRestriction(bindingPos)) {
            return ItemStack.EMPTY;
        }

        BlockEntity te = maid.level().getBlockEntity(bindingPos);
        if (te == null) {
            return ItemStack.EMPTY;
        }

        for (var type : ChestManager.getAllChestTypes()) {
            if (type.isChest(te)) {
                IItemHandler chestInv = te.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
                if (chestInv != null) {
                    byte[] slotConfig = ItemWirelessIO.getSlotConfig(wirelessIO);
                    // 动态获取槽位数：slotConfig有就用它的长度，没有就用默认38（女仆标准槽位数）
                    int slotNum = slotConfig != null ? slotConfig.length : 38;
                    boolean[] slotConfigData = slotConfig != null ? bytes2Booleans(slotConfig, slotNum) : null;

                    // 遍历箱子找食物
                    for (int i = 0; i < chestInv.getSlots(); i++) {
                        if (slotConfigData != null && i < slotConfigData.length && slotConfigData[i]) {
                            continue; // 槽位禁用了，跳过
                        }

                        ItemStack stack = chestInv.getStackInSlot(i);
                        if (!stack.isEmpty() && isValidFood(stack)) {
                            boolean allowMove = WirelessIOHelper.isItemAllowed(wirelessIO, stack);

                            if (allowMove) {
                                ItemStack extracted = chestInv.extractItem(i, 1, false);
                                if (!mainHand.isEmpty()) {
                                    // 主手有东西，先塞回女仆背包
                                    var maidBackpack = maid.getAvailableBackpackInv();
                                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(maidBackpack, mainHand, false);
                                    if (!remaining.isEmpty()) {
                                        // 背包满了，塞回箱子
                                        remaining = ItemHandlerHelper.insertItemStacked(chestInv, remaining, false);
                                        if (!remaining.isEmpty()) {
                                            // 箱子也满了，还原
                                            chestInv.insertItem(i, extracted, false);
                                            return ItemStack.EMPTY;
                                        }
                                    }
                                }
                                maid.setItemInHand(InteractionHand.MAIN_HAND, extracted);
                                return extracted;
                            }
                        }
                    }
                }
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    // 从记忆里获取附近实体，标准写法
    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
    }
}
