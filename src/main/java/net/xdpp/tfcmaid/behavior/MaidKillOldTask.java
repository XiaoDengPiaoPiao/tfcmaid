package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.WirelessIOBauble;
import com.google.common.collect.ImmutableMap;
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
import net.xdpp.tfcmaid.util.WirelessIOHelper;

import static com.github.tartaricacid.touhoulittlemaid.util.BytesBooleansConvert.bytes2Booleans;

// 女仆击杀衰老动物的任务
// 逻辑：找衰老的动物 -> 走过去 -> 清空背包 -> 一刀砍死
// 为什么要清空背包？为了给掉落物腾地方，顺便用隙间存起来
public class MaidKillOldTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 20; // 检查间隔，20tick也就是1秒
    private final float speedModifier;
    private LivingEntity targetAnimal = null;

    public MaidKillOldTask(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.speedModifier = speedModifier;
        this.setMaxCheckRate(MAX_DELAY_TIME);
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        targetAnimal = null;

        // 先检查主手有没有武器，没有就直接跳过
        ItemStack mainHand = maid.getMainHandItem();
        if (!isWeapon(mainHand)) {
            return;
        }

        // 找目标：在工作范围内、活着、是TFC动物、不是宠物（OwnableEntity）、是衰老状态、能走到
        this.getEntities(maid)
                .find(e -> maid.isWithinRestriction(e.blockPosition()))
                .filter(LivingEntity::isAlive)
                .filter(e -> e instanceof TFCAnimalProperties && !(e instanceof OwnableEntity))
                .filter(e -> ((TFCAnimalProperties) e).getAgeType() == TFCAnimalProperties.Age.OLD)
                .filter(maid::canPathReach)
                .findFirst()
                .ifPresent(e -> {
                    targetAnimal = e;
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, e, this.speedModifier, 0);
                });

        // 走到了就动手
        if (targetAnimal != null && targetAnimal.closerThan(maid, 2)) {
            killAnimal(maid, targetAnimal);
            targetAnimal = null;
        }
    }

    // 判断是不是武器：有耐久值或者能挥剑就行
    private boolean isWeapon(ItemStack stack) {
        return stack.getDamageValue() > 0 || stack.canPerformAction(net.minecraftforge.common.ToolActions.SWORD_DIG);
    }

    // 击杀逻辑：先清空背包，再挥一刀
    // 清空背包是关键，不然掉的东西捡不起来
    private void killAnimal(EntityMaid maid, LivingEntity animal) {
        ServerLevel level = (ServerLevel) maid.level();
        
        if (!level.isClientSide) {
            clearBackpackToWirelessIO(maid);
            maid.swing(InteractionHand.MAIN_HAND);
            animal.hurt(maid.damageSources().mobAttack(maid), Float.MAX_VALUE); // Float.MAX_VALUE直接秒杀
        }
    }
    
    // 清空背包到隙间箱子
    // 主手的武器不动，只动背包里的东西
    private void clearBackpackToWirelessIO(EntityMaid maid) {
        ItemStack wirelessIO = WirelessIOHelper.getWirelessIOBauble(maid);
        if (wirelessIO.isEmpty()) {
            return;
        }

        var bindingPos = ItemWirelessIO.getBindingPos(wirelessIO);
        if (bindingPos == null || !maid.isWithinRestriction(bindingPos)) {
            return;
        }

        BlockEntity te = maid.level().getBlockEntity(bindingPos);
        if (te == null) {
            return;
        }

        for (var type : ChestManager.getAllChestTypes()) {
            if (type.isChest(te)) {
                IItemHandler chestInv = te.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
                if (chestInv != null) {
                    byte[] slotConfig = ItemWirelessIO.getSlotConfig(wirelessIO);
                    // 动态获取槽位数：slotConfig有就用它的长度，没有就用默认38（女仆标准槽位数）
                    int slotNum = slotConfig != null ? slotConfig.length : 38;
                    boolean[] slotConfigData = slotConfig != null ? bytes2Booleans(slotConfig, slotNum) : null;

                    // 遍历背包，一个一个挪
                    var backpack = maid.getAvailableBackpackInv();
                    for (int i = 0; i < backpack.getSlots(); i++) {
                        ItemStack stack = backpack.getStackInSlot(i);
                        if (stack.isEmpty()) {
                            continue;
                        }

                        boolean allowMove = WirelessIOHelper.isItemAllowed(wirelessIO, stack);
                        if (allowMove) {
                            ItemStack remaining = WirelessIOBauble.insertItemStacked(chestInv, stack.copy(), false, slotConfigData);
                            int movedCount = stack.getCount() - remaining.getCount();
                            if (movedCount > 0) {
                                backpack.extractItem(i, movedCount, false);
                            }
                        }
                    }
                }
                break;
            }
        }
    }

    // 从记忆里获取附近的实体，标准写法
    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
    }
}
