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

public class MaidMilkTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 12;
    private static final int SLOT_NUM = 38;
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

        // 查找并装备挤奶容器
        if (!findAndEquipMilkContainer(maid)) {
            // 没有可用容器，停止任务
            return;
        }

        this.getEntities(maid)
                .find(e -> maid.isWithinRestriction(e.blockPosition()))
                .filter(LivingEntity::isAlive)
                .filter(e -> e instanceof DairyAnimal)
                .filter(maid::canPathReach)
                .findFirst()
                .ifPresent(e -> {
                    dairyAnimal = e;
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, e, this.speedModifier, 0);
                });

        if (dairyAnimal != null && dairyAnimal.closerThan(maid, 2)) {
            DairyAnimal animal = (DairyAnimal) dairyAnimal;
            ItemStack held = maid.getMainHandItem();
            
            if (!held.isEmpty()) {
                // 确保只处理一个桶
                ItemStack singleBucket = held.copyWithCount(1);
                IFluidHandlerItem destFluidItemHandler = Helpers.getCapability(singleBucket, Capabilities.FLUID_ITEM);

                if (destFluidItemHandler != null) {
                    if (animal.isReadyForAnimalProduct()) {
                        final FluidStack milk = new FluidStack(animal.getMilkFluid(), FluidHelpers.BUCKET_VOLUME);
                        final AnimalProductEvent event = new AnimalProductEvent(worldIn, maid.blockPosition(), null, animal, milk, singleBucket, 1);

                        if (!MinecraftForge.EVENT_BUS.post(event)) {
                            // 尝试填充牛奶
                            int filled = destFluidItemHandler.fill(milk, IFluidHandlerItem.FluidAction.SIMULATE);
                            if (filled > 0) {
                                // 执行填充
                                destFluidItemHandler.fill(milk, IFluidHandlerItem.FluidAction.EXECUTE);
                                ItemStack filledBucket = destFluidItemHandler.getContainer();
                                
                                // 处理物品更新
                                if (held.getCount() == 1) {
                                    // 只有一个桶，直接替换
                                    maid.setItemInHand(InteractionHand.MAIN_HAND, filledBucket);
                                } else {
                                    // 有多个桶，减少一个
                                    held.shrink(1);
                                    // 尝试将装满的桶放入背包
                                    var backpack = maid.getAvailableBackpackInv();
                                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, filledBucket, false);
                                    
                                    // 如果背包满了，尝试通过无线IO存入箱子
                                    if (!remaining.isEmpty()) {
                                        remaining = WirelessIOHelper.tryInsertToChest(maid, remaining);
                                    }
                                    
                                    // 如果还是有剩余，停止挤奶
                                    if (!remaining.isEmpty()) {
                                        maid.setItemInHand(InteractionHand.MAIN_HAND, held);
                                        dairyAnimal = null;
                                        return;
                                    }
                                }
                                
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

    private boolean findAndEquipMilkContainer(EntityMaid maid) {
        ItemStack mainHand = maid.getMainHandItem();
        
        // 检查主手是否有可用容器
        if (!mainHand.isEmpty()) {
            ItemStack singleStack = mainHand.copyWithCount(1);
            IFluidHandlerItem handler = Helpers.getCapability(singleStack, Capabilities.FLUID_ITEM);
            if (handler != null && canAcceptMoreMilk(handler)) {
                return true;
            }
        }

        // 从背包中查找可用容器
        var backpack = maid.getAvailableBackpackInv();
        for (int i = 0; i < backpack.getSlots(); i++) {
            ItemStack stack = backpack.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStack singleStack = stack.copyWithCount(1);
                IFluidHandlerItem handler = Helpers.getCapability(singleStack, Capabilities.FLUID_ITEM);
                if (handler != null && canAcceptMoreMilk(handler)) {
                    // 只提取一个桶
                    ItemStack extracted = backpack.extractItem(i, 1, false);
                    if (!mainHand.isEmpty()) {
                        // 将主手物品放回背包
                        ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, mainHand, false);
                        // 如果背包满了，尝试通过无线IO存入箱子
                        if (!remaining.isEmpty()) {
                            remaining = WirelessIOHelper.tryInsertToChest(maid, remaining);
                        }
                        // 如果还是有剩余，放回主手，放弃装备
                        if (!remaining.isEmpty()) {
                            backpack.insertItem(i, extracted, false);
                            return false;
                        }
                    }
                    // 将提取的桶放到主手
                    maid.setItemInHand(InteractionHand.MAIN_HAND, extracted);
                    return true;
                }
            }
        }

        // 背包里没有，尝试从wireless_io绑定的箱子中获取
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
                                // 检查过滤规则
                                boolean isBlacklist = ItemWirelessIO.isBlacklist(wirelessIO);
                                IItemHandler filterList = ItemWirelessIO.getFilterList(wirelessIO);
                                byte[] slotConfig = ItemWirelessIO.getSlotConfig(wirelessIO);
                                boolean[] slotConfigData = slotConfig != null ? bytes2Booleans(slotConfig, SLOT_NUM) : null;

                                // 从箱子中查找可用容器
                                for (int i = 0; i < chestInv.getSlots(); i++) {
                                    if (slotConfigData != null && i < slotConfigData.length && slotConfigData[i]) {
                                        continue;
                                    }

                                    ItemStack stack = chestInv.getStackInSlot(i);
                                    if (!stack.isEmpty()) {
                                        // 检查物品是否允许移动
                                        boolean allowMove = WirelessIOHelper.isItemAllowed(wirelessIO, stack);

                                        if (allowMove) {
                                            ItemStack singleStack = stack.copyWithCount(1);
                                            IFluidHandlerItem handler = Helpers.getCapability(singleStack, Capabilities.FLUID_ITEM);
                                            if (handler != null && canAcceptMoreMilk(handler)) {
                                                // 提取一个桶
                                                ItemStack extracted = chestInv.extractItem(i, 1, false);
                                                if (!mainHand.isEmpty()) {
                                                    // 将主手物品放回背包
                                                    var maidBackpack = maid.getAvailableBackpackInv();
                                                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(maidBackpack, mainHand, false);
                                                    if (!remaining.isEmpty()) {
                                                        // 如果背包满了，尝试放入箱子
                                                        remaining = ItemHandlerHelper.insertItemStacked(chestInv, remaining, false);
                                                        if (!remaining.isEmpty()) {
                                                            // 还是满的，放回主手，放弃
                                                            chestInv.insertItem(i, extracted, false);
                                                            return false;
                                                        }
                                                    }
                                                }
                                                // 将提取的桶放到主手
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

        // 没有找到任何可用容器
        return false;
    }



    private boolean canAcceptMoreMilk(IFluidHandlerItem handler) {
        int simulatedFill = handler.fill(new FluidStack(net.minecraftforge.common.ForgeMod.MILK.get(), FluidHelpers.BUCKET_VOLUME), IFluidHandlerItem.FluidAction.SIMULATE);
        return simulatedFill > 0;
    }

    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
    }
}
