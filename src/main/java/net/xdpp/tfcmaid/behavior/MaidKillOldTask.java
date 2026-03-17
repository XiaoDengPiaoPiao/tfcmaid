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

public class MaidKillOldTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 20;
    private static final int SLOT_NUM = 38;
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

        ItemStack mainHand = maid.getMainHandItem();
        if (!isWeapon(mainHand)) {
            return;
        }

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

        if (targetAnimal != null && targetAnimal.closerThan(maid, 2)) {
            killAnimal(maid, targetAnimal);
            targetAnimal = null;
        }
    }

    private boolean isWeapon(ItemStack stack) {
        return stack.getDamageValue() > 0 || stack.canPerformAction(net.minecraftforge.common.ToolActions.SWORD_DIG);
    }

    private void killAnimal(EntityMaid maid, LivingEntity animal) {
        ServerLevel level = (ServerLevel) maid.level();
        
        if (!level.isClientSide) {
            clearBackpackToWirelessIO(maid);
            maid.swing(InteractionHand.MAIN_HAND);
            animal.hurt(maid.damageSources().mobAttack(maid), Float.MAX_VALUE);
        }
    }
    
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
                    boolean isBlacklist = ItemWirelessIO.isBlacklist(wirelessIO);
                    IItemHandler filterList = ItemWirelessIO.getFilterList(wirelessIO);
                    byte[] slotConfig = ItemWirelessIO.getSlotConfig(wirelessIO);
                    boolean[] slotConfigData = slotConfig != null ? bytes2Booleans(slotConfig, SLOT_NUM) : null;

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

    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
    }
}
