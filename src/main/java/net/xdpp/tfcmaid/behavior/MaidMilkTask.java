package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import com.google.common.collect.ImmutableMap;
import net.dries007.tfc.common.capabilities.Capabilities;
import net.dries007.tfc.common.entities.livestock.DairyAnimal;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.events.AnimalProductEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.xdpp.tfcmaid.util.WirelessIOHelper;

import static com.github.tartaricacid.touhoulittlemaid.util.BytesBooleansConvert.bytes2Booleans;

// 这段代码的实现看起来很混乱，但是没有问题，为什么？
// 这得益于群峦挤奶容器的设计，挤奶时只有单个容器能操作，强行用多个容器堆叠在一个hand会出现异常，导致挤奶失败，截至代码编写之日
// 群峦还是把挤奶的异常没给我抛出来自己给吞了，我一点办法都没有，只好想到了接管整个挤奶时容器消耗的流程来解决这个问题
// 这堆代码的核心逻辑是 检查动物能不能挤奶->检查桶处理桶->处理桶消耗->能挤奶把奶用fill方法填充到容器里
// 我实现的逻辑很混乱，如果看到这段代码的人，觉得有问题，想修改的话，除非群峦后续把这方面的实现给优化好，或者你有更好的解决方案，亦或是来处理这边的bug
// 不然我不建议动这里的任何代码：）小登已经燃尽了

// 女仆挤奶任务 - 核心是处理容器、堆叠、隙间这些细节
public class MaidMilkTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 12; // 检查间隔，12tick也就是0.6秒，挤奶动作频繁点
    private final float speedModifier;
    private LivingEntity dairyAnimal = null;

    public MaidMilkTask(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        dairyAnimal = null;

        // 先找容器，找不到就停止
        if (!findAndEquipMilkContainer(maid)) {
            return;
        }

        // 找附近的奶牛，条件是：在范围内、活着、是DairyAnimal、能走到
        java.util.List<LivingEntity> candidates = this.getEntities(maid)
                .find(e -> maid.isWithinRestriction(e.blockPosition()))
                .filter(LivingEntity::isAlive)
                .filter(e -> e instanceof DairyAnimal)
                .filter(maid::canPathReach)
                .toList();
        
        if (!candidates.isEmpty()) {
            int index = maid.getRandom().nextInt(candidates.size());
            dairyAnimal = candidates.get(index);
            BehaviorUtils.setWalkAndLookTargetMemories(maid, dairyAnimal, this.speedModifier, 0);
        }

        // 走到了就开始挤奶
        if (dairyAnimal != null && dairyAnimal.closerThan(maid, 2)) {
            DairyAnimal animal = (DairyAnimal) dairyAnimal;
            ItemStack held = maid.getMainHandItem();
            
            if (!held.isEmpty()) {
                // 关键：永远只处理单个桶，避免堆叠的坑(群峦代码导致的，我只想到这个解法)
                ItemStack singleBucket = held.copyWithCount(1);
                IFluidHandlerItem destFluidItemHandler = Helpers.getCapability(singleBucket, Capabilities.FLUID_ITEM);

                if (destFluidItemHandler != null) {
                    if (animal.isReadyForAnimalProduct()) { // 动物准备好了才挤
                        final FluidStack milk = new FluidStack(animal.getMilkFluid(), FluidHelpers.BUCKET_VOLUME);
                        final AnimalProductEvent event = new AnimalProductEvent(worldIn, maid.blockPosition(), null, animal, milk, singleBucket, 1);

                        if (!MinecraftForge.EVENT_BUS.post(event)) { // 先触发事件，给其他mod插手的机会
                            // 模拟填充一下，看看能不能装下
                            int filled = destFluidItemHandler.fill(milk, IFluidHandlerItem.FluidAction.SIMULATE);
                            if (filled > 0) {
                                // 真的填充
                                destFluidItemHandler.fill(milk, IFluidHandlerItem.FluidAction.EXECUTE);
                                ItemStack filledBucket = destFluidItemHandler.getContainer();
                                
                                // 处理物品更新逻辑，分两种情况
                                if (held.getCount() == 1) {
                                    // 只有一个桶，直接替换主手就行
                                    maid.setItemInHand(InteractionHand.MAIN_HAND, filledBucket);
                                } else {
                                    // 有多个桶，先减少一个
                                    held.shrink(1);
                                    // 把装满的桶往背包塞
                                    var backpack = maid.getAvailableBackpackInv();
                                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, filledBucket, false);
                                    
                                    // 背包满了？传隙间
                                    if (!remaining.isEmpty()) {
                                        remaining = WirelessIOHelper.tryInsertToChest(maid, remaining);
                                    }
                                    
                                    // 还是塞不下？还原状态，不挤了
                                    if (!remaining.isEmpty()) {
                                        maid.setItemInHand(InteractionHand.MAIN_HAND, held);
                                        dairyAnimal = null;
                                        return;
                                    }
                                }
                                
                                // 挤奶成功后的收尾工作
                                animal.setProductsCooldown();
                                animal.addUses(event.getUses());
                                maid.playSound(SoundEvents.COW_MILK, 1.0f, 1.0f);
                                maid.swing(InteractionHand.MAIN_HAND);
                            }
                        }
                    }
                }
            }
            dairyAnimal = null;
        }
    }

    // 查找并装备挤奶容器的核心方法
    // 查找顺序：主手 -> 背包 -> 箱子
    private boolean findAndEquipMilkContainer(EntityMaid maid) {
        ItemStack mainHand = maid.getMainHandItem();
        
        // 第一步：先看看主手有没有能用的容器
        if (!mainHand.isEmpty()) {
            ItemStack singleStack = mainHand.copyWithCount(1);
            IFluidHandlerItem handler = Helpers.getCapability(singleStack, Capabilities.FLUID_ITEM);
            if (handler != null && canAcceptMoreMilk(handler)) {
                return true; // 主手有，直接用
            }
        }

        // 第二步：主手没有，翻背包找
        var backpack = maid.getAvailableBackpackInv();
        for (int i = 0; i < backpack.getSlots(); i++) {
            ItemStack stack = backpack.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStack singleStack = stack.copyWithCount(1);
                IFluidHandlerItem handler = Helpers.getCapability(singleStack, Capabilities.FLUID_ITEM);
                if (handler != null && canAcceptMoreMilk(handler)) {
                    // 找到了，只提取一个（避免堆叠问题）
                    ItemStack extracted = backpack.extractItem(i, 1, false);
                    if (!mainHand.isEmpty()) {
                        // 主手有东西，先塞回背包
                        ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, mainHand, false);
                        // 背包满了？试试隙间
                        if (!remaining.isEmpty()) {
                            remaining = WirelessIOHelper.tryInsertToChest(maid, remaining);
                        }
                        // 还是塞不下？还原状态
                        if (!remaining.isEmpty()) {
                            backpack.insertItem(i, extracted, false);
                            return false;
                        }
                    }
                    // 把找到的空桶放到主手
                    maid.setItemInHand(InteractionHand.MAIN_HAND, extracted);
                    return true;
                }
            }
        }

        // 第三步：背包也没有，试试隙间绑定的箱子
        ItemStack wirelessIO = WirelessIOHelper.getWirelessIOBauble(maid);
        if (!wirelessIO.isEmpty()) {
            BlockPos bindingPos = ItemWirelessIO.getBindingPos(wirelessIO);
            if (bindingPos != null && maid.isWithinRestriction(bindingPos)) {
                BlockEntity te = maid.level().getBlockEntity(bindingPos);
                if (te != null) {
                    for (var type : ChestManager.getAllChestTypes()) {
                        if (type.isChest(te)) {
                            IItemHandler chestInv = te.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
                            if (chestInv != null) {
                                // 检查过滤规则相关配置
                                byte[] slotConfig = ItemWirelessIO.getSlotConfig(wirelessIO);
                                // 动态获取槽位数：slotConfig有就用它的长度，没有就用默认38（女仆标准槽位数）
                                int slotNum = slotConfig != null ? slotConfig.length : 38;
                                boolean[] slotConfigData = slotConfig != null ? bytes2Booleans(slotConfig, slotNum) : null;

                                // 遍历箱子找能用的容器
                                for (int i = 0; i < chestInv.getSlots(); i++) {
                                    if (slotConfigData != null && i < slotConfigData.length && slotConfigData[i]) {
                                        continue; // 这个槽位被禁用了，跳过
                                    }

                                    ItemStack stack = chestInv.getStackInSlot(i);
                                    if (!stack.isEmpty()) {
                                        // 先检查这个物品能不能移动（黑白名单过滤）
                                        boolean allowMove = WirelessIOHelper.isItemAllowed(wirelessIO, stack);

                                        if (allowMove) {
                                            ItemStack singleStack = stack.copyWithCount(1);
                                            IFluidHandlerItem handler = Helpers.getCapability(singleStack, Capabilities.FLUID_ITEM);
                                            if (handler != null && canAcceptMoreMilk(handler)) {
                                                // 找到了，提取一个
                                                ItemStack extracted = chestInv.extractItem(i, 1, false);
                                                if (!mainHand.isEmpty()) {
                                                    // 主手有东西，先塞回女仆背包
                                                    var maidBackpack = maid.getAvailableBackpackInv();
                                                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(maidBackpack, mainHand, false);
                                                    if (!remaining.isEmpty()) {
                                                        // 背包满了？塞回箱子
                                                        remaining = ItemHandlerHelper.insertItemStacked(chestInv, remaining, false);
                                                        if (!remaining.isEmpty()) {
                                                            // 箱子也满了？那算了，还原
                                                            chestInv.insertItem(i, extracted, false);
                                                            return false;
                                                        }
                                                    }
                                                }
                                                // 把空桶放到主手
                                                maid.setItemInHand(InteractionHand.MAIN_HAND, extracted);
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }

        // 哪里都找不到，返回false，任务GG
        return false;
    }

    // 检查容器还能不能装下更多牛奶（模拟填充）
    private boolean canAcceptMoreMilk(IFluidHandlerItem handler) {
        int simulatedFill = handler.fill(new FluidStack(net.minecraftforge.common.ForgeMod.MILK.get(), FluidHelpers.BUCKET_VOLUME), IFluidHandlerItem.FluidAction.SIMULATE);
        return simulatedFill > 0;
    }

    // 从记忆里获取附近实体，标准写法
    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
    }
}
