package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.google.common.collect.ImmutableMap;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.QuernBlockEntity;
import net.dries007.tfc.common.recipes.QuernRecipe;
import net.dries007.tfc.common.recipes.inventory.ItemStackInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.xdpp.tfcmaid.mixin.QuernBlockEntityAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * 推磨工作任务类
 * 继承自 MaidLongRunningTask，实现推磨操作的完整流程
 * 工作流程：
 * 1. 检查推磨是否有 handstone（手石），如果没有，从背包找一个放上去
 * 2. 检查输出槽，如果有物品，取出来放到女仆背包
 * 3. 按顺序找可以磨的物品：主手 → 副手 → 背包
 * 4. 将可磨物品放到推磨输入槽，调用 startGrinding() 开始推磨
 * 5. 等待推磨完成（90 tick），自动结束后收集输出
 * 功能特点：
 * - 支持从背包获取手石
 * - 按优先级顺序找可磨物品（主手 > 副手 > 背包）
 * - 距离检查：只有在推磨4格范围内才能工作
 */
public class MaidQuernWorkTask extends MaidLongRunningTask {
    private static final Logger LOGGER = LoggerFactory.getLogger("MaidQuernWorkTask");

    /**
     * 推磨槽位定义
     */
    private static final int SLOT_HANDSTONE = 0;
    private static final int SLOT_INPUT = 1;
    private static final int SLOT_OUTPUT = 2;

    /**
     * 通过反射获取推磨的 inventory 字段
     */
    private static Field inventoryField = null;

    static {
        try {
            Class<?> clazz = Class.forName("net.dries007.tfc.common.blockentities.InventoryBlockEntity");
            inventoryField = clazz.getDeclaredField("inventory");
            inventoryField.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("Failed to get inventory field", e);
        }
    }

    /**
     * 目标推磨位置
     */
    private BlockPos targetPos = null;

    public MaidQuernWorkTask(double closeEnoughDist) {
        super(ImmutableMap.of(
                InitEntities.TARGET_POS.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, EntityMaid maid) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(posWrapper -> {
            BlockPos pos = posWrapper.currentBlockPosition();
            this.targetPos = pos;
            BlockEntity be = world.getBlockEntity(pos);
            return be instanceof QuernBlockEntity;
        }).orElse(false);
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTime) {
    }

    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        if (this.targetPos == null) {
            return;
        }

        double distSqr = maid.distanceToSqr(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D);
        if (distSqr > 16.0D) { // 4格范围
            return;
        }

        maid.getLookControl().setLookAt(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D);

        BlockEntity be = world.getBlockEntity(this.targetPos);
        if (!(be instanceof QuernBlockEntity quern)) {
            return;
        }
        var inventory = getQuernInventory(quern);
        if (inventory == null) {
            LOGGER.error("MaidQuernWorkTask tick: failed to get quern inventory");
            return;
        }

        /**
         * 1. 先检查输出槽，如果有东西就取出来给女仆
         */
        ItemStack outputStack = inventory.getStackInSlot(SLOT_OUTPUT);
        if (!outputStack.isEmpty()) {
            ItemStack extracted = outputStack.copy();
            inventory.setStackInSlot(SLOT_OUTPUT, ItemStack.EMPTY);
            ItemsUtil.giveItemToMaid(maid, extracted);
            quern.markForSync();
        }

        /**
         * 2. 检查推磨是否有 handstone（手石），如果没有，从女仆背包找一个放上去
         */
        ItemStack handstoneStack = inventory.getStackInSlot(SLOT_HANDSTONE);
        if (handstoneStack.isEmpty()) {
            ItemStack foundHandstone = findHandstoneInInventory(maid);
            if (!foundHandstone.isEmpty()) {
                inventory.setStackInSlot(SLOT_HANDSTONE, foundHandstone);
                quern.markForSync();
            }
        }

        /**
         * 3. 检查推磨是否正在工作，如果正在工作就什么都不做
         */
        QuernBlockEntityAccessor accessor = (QuernBlockEntityAccessor) quern;
        if (quern.isGrinding()) {
            return;
        }

        /**
         * 4. 检查输入槽是否有物品可以磨
         */
        ItemStack inputStack = inventory.getStackInSlot(SLOT_INPUT);
        if (!inputStack.isEmpty()) {
            ItemStackInventory wrapper = new ItemStackInventory(inputStack);
            QuernRecipe recipe = QuernRecipe.getRecipe(world, wrapper);
            if (recipe != null && recipe.matches(wrapper, world)) {
                quern.startGrinding();
                return;
            }
        }

        /**
         * 5. 输入槽空或者没有合适配方，从女仆背包找可磨物品
         * 按顺序：主手 → 副手 → 背包
         */
        ItemStack itemToGrind = findGrindableItem(maid, world);
        if (!itemToGrind.isEmpty()) {
            inventory.setStackInSlot(SLOT_INPUT, itemToGrind);
            quern.markForSync();
            quern.startGrinding();
        }
    }

    /**
     * 通过反射获取推磨的 inventory
     *
     * @param quern 推磨方块实体
     * @return 推磨的物品处理器
     */
    private IItemHandlerModifiable getQuernInventory(QuernBlockEntity quern) {
        if (inventoryField == null) {
            LOGGER.error("MaidQuernWorkTask: inventoryField is null");
            return null;
        }
        try {
            return (IItemHandlerModifiable) inventoryField.get(quern);
        } catch (Exception e) {
            LOGGER.error("MaidQuernWorkTask: Failed to get quern inventory", e);
            return null;
        }
    }

    /**
     * 从女仆背包找手石
     *
     * @param maid 女仆实体
     * @return 找到的手石，找不到返回空
     */
    private ItemStack findHandstoneInInventory(EntityMaid maid) {
        for (int i = 0; i < maid.getMaidInv().getSlots(); i++) {
            ItemStack stack = maid.getMaidInv().getStackInSlot(i);
            if (stack.is(TFCTags.Items.HANDSTONE)) {
                ItemStack result = stack.copy();
                result.setCount(1);
                stack.shrink(1);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 从女仆找可磨的物品，按优先级：主手 → 副手 → 背包
     * 找到后消耗1个物品
     *
     * @param maid 女仆实体
     * @param world 世界
     * @return 找到的可磨物品（1个），找不到返回空
     */
    private ItemStack findGrindableItem(EntityMaid maid, ServerLevel world) {
        // 先检查主手
        ItemStack mainHand = maid.getMainHandItem();
        if (!mainHand.isEmpty()) {
            ItemStackInventory wrapper = new ItemStackInventory(mainHand);
            QuernRecipe recipe = QuernRecipe.getRecipe(world, wrapper);
            if (recipe != null && recipe.matches(wrapper, world)) {
                ItemStack result = mainHand.copy();
                result.setCount(1);
                mainHand.shrink(1);
                return result;
            }
        }

        // 再检查副手
        ItemStack offHand = maid.getOffhandItem();
        if (!offHand.isEmpty()) {
            ItemStackInventory wrapper = new ItemStackInventory(offHand);
            QuernRecipe recipe = QuernRecipe.getRecipe(world, wrapper);
            if (recipe != null && recipe.matches(wrapper, world)) {
                ItemStack result = offHand.copy();
                result.setCount(1);
                offHand.shrink(1);
                return result;
            }
        }

        // 最后检查背包
        for (int i = 0; i < maid.getMaidInv().getSlots(); i++) {
            ItemStack stack = maid.getMaidInv().getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStackInventory wrapper = new ItemStackInventory(stack);
                QuernRecipe recipe = QuernRecipe.getRecipe(world, wrapper);
                if (recipe != null && recipe.matches(wrapper, world)) {
                    ItemStack result = stack.copy();
                    result.setCount(1);
                    stack.shrink(1);
                    return result;
                }
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, EntityMaid maid, long gameTime) {
        if (this.targetPos == null) {
            return false;
        }
        BlockEntity be = world.getBlockEntity(this.targetPos);
        if (!(be instanceof QuernBlockEntity)) {
            return false;
        }
        double distSqr = maid.distanceToSqr(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D);
        if (distSqr > 16.0D) { // 4格范围
            return false;
        }
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).isPresent();
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTime) {
        this.targetPos = null;
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}
