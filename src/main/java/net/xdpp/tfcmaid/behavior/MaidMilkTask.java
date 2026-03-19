package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.dries007.tfc.common.capabilities.Capabilities;
import net.dries007.tfc.common.entities.livestock.DairyAnimal;
import net.dries007.tfc.common.fluids.FluidHelpers;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.events.AnimalProductEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.xdpp.tfcmaid.util.MaidEquipmentHelper;

/**
 * 女仆挤奶任务 - 核心是处理容器、堆叠这些细节
 */
public class MaidMilkTask extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 12;
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

        if (!findAndEquipMilkContainer(maid)) {
            return;
        }

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

        if (dairyAnimal != null && dairyAnimal.closerThan(maid, 2)) {
            DairyAnimal animal = (DairyAnimal) dairyAnimal;
            ItemStack held = maid.getMainHandItem();
            
            if (!held.isEmpty()) {
                ItemStack singleBucket = held.copyWithCount(1);
                IFluidHandlerItem destFluidItemHandler = Helpers.getCapability(singleBucket, Capabilities.FLUID_ITEM);

                if (destFluidItemHandler != null) {
                    if (animal.isReadyForAnimalProduct()) {
                        final FluidStack milk = new FluidStack(animal.getMilkFluid(), FluidHelpers.BUCKET_VOLUME);
                        final AnimalProductEvent event = new AnimalProductEvent(worldIn, maid.blockPosition(), null, animal, milk, singleBucket, 1);

                        if (!MinecraftForge.EVENT_BUS.post(event)) {
                            int filled = destFluidItemHandler.fill(milk, IFluidHandlerItem.FluidAction.SIMULATE);
                            if (filled > 0) {
                                destFluidItemHandler.fill(milk, IFluidHandlerItem.FluidAction.EXECUTE);
                                ItemStack filledBucket = destFluidItemHandler.getContainer();
                                
                                if (held.getCount() == 1) {
                                    maid.setItemInHand(InteractionHand.MAIN_HAND, filledBucket);
                                } else {
                                    held.shrink(1);
                                    var backpack = maid.getAvailableBackpackInv();
                                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, filledBucket, false);
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
        return MaidEquipmentHelper.findAndEquipItemWithValidation(maid, stack -> {
            ItemStack singleStack = stack.copyWithCount(1);
            IFluidHandlerItem handler = Helpers.getCapability(singleStack, Capabilities.FLUID_ITEM);
            if (handler != null && canAcceptMoreMilk(handler)) {
                return singleStack;
            }
            return null;
        });
    }

    private boolean canAcceptMoreMilk(IFluidHandlerItem handler) {
        int simulatedFill = handler.fill(new FluidStack(net.minecraftforge.common.ForgeMod.MILK.get(), FluidHelpers.BUCKET_VOLUME), IFluidHandlerItem.FluidAction.SIMULATE);
        return simulatedFill > 0;
    }

    private NearestVisibleLivingEntities getEntities(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
    }
}
