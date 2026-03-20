package net.xdpp.tfcmaid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.google.common.collect.ImmutableMap;
import net.dries007.tfc.common.blockentities.LoomBlockEntity;
import net.dries007.tfc.common.recipes.LoomRecipe;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.util.Helpers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.xdpp.tfcmaid.mixin.LoomBlockEntityAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 织机工作任务类
 * 继承自 MaidLongRunningTask，实现织机操作的完整流程
 * 采用状态机模式，分为以下几个阶段：
 * 1. STATE_FIND_RECIPE：查找与主手物品匹配的配方
 * 2. STATE_ADD_ITEM：将原料添加到织机
 * 3. STATE_WORKING：操作织机推进进度
 * 4. STATE_COLLECT：收取成品并清理织机
 * 功能特点：
 * - 支持检查织机已有原料，只补充不足的部分
 * - 无论是否有足够材料做下一次，都会先收取成品
 * - 距离检查：只有在织机4格范围内才能工作
 */
public class MaidLoomWorkTask extends MaidLongRunningTask {
    private static final Logger LOGGER = LoggerFactory.getLogger("MaidLoomWorkTask");
    
    /**
     * 状态定义
     */
    private static final int STATE_FIND_RECIPE = 0;
    private static final int STATE_ADD_ITEM = 1;
    private static final int STATE_WORKING = 2;
    private static final int STATE_COLLECT = 3;
    
    /**
     * 织机槽位定义
     * SLOT_RECIPE：原料槽（0）
     * SLOT_OUTPUT：输出槽（1）
     */
    private static final int SLOT_RECIPE = 0;
    private static final int SLOT_OUTPUT = 1;

    /**
     * 通过反射获取织机的 inventory 字段
     * TFC的织机继承自 InventoryBlockEntity，其 inventory 字段是受保护的
     * 使用反射绕过访问限制
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
     * 当前状态
     */
    private int state = STATE_FIND_RECIPE;
    
    /**
     * 当前使用的配方
     */
    private LoomRecipe currentRecipe = null;
    
    /**
     * 目标织机位置
     */
    private BlockPos targetPos = null;

    public MaidLoomWorkTask(double closeEnoughDist) {
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
            if (!(be instanceof LoomBlockEntity)) {
                return false;
            }
            ItemStack mainHand = maid.getMainHandItem();
            return !mainHand.isEmpty();
        }).orElse(false);
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTime) {
        this.state = STATE_FIND_RECIPE;
        this.currentRecipe = null;
    }

    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        if (this.targetPos == null) {
            return;
        }

        double distSqr = maid.distanceToSqr(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D);
        if (distSqr > 16.0D) { // 4格范围（4^2=16）
            return;
        }

        maid.getLookControl().setLookAt(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D);

        BlockEntity be = world.getBlockEntity(this.targetPos);
        if (!(be instanceof LoomBlockEntity loom)) {
            return;
        }
        LoomBlockEntityAccessor accessor = (LoomBlockEntityAccessor) loom;
        var inventory = getLoomInventory(loom);
        if (inventory == null) {
            LOGGER.error("MaidLoomWorkTask tick: failed to get loom inventory");
            return;
        }

        switch (this.state) {
            case STATE_FIND_RECIPE: {
                /**
                 * 优先检查输出槽是否有成品
                 * 如果有，先去收取，无论是否有材料做下一次
                 */
                ItemStack slotOutput = inventory.getStackInSlot(SLOT_OUTPUT);
                if (!slotOutput.isEmpty()) {
                    this.state = STATE_COLLECT;
                    return;
                }
                
                /**
                 * 检查主手和副手条件
                 */
                ItemStack mainHand = maid.getMainHandItem();
                if (mainHand.isEmpty()) {
                    this.state = STATE_FIND_RECIPE;
                    return;
                }
                ItemStack offhand = maid.getOffhandItem();
                if (offhand.isEmpty() || !offhand.is(net.minecraft.world.item.Items.STICK)) {
                    this.state = STATE_FIND_RECIPE;
                    return;
                }

                /**
                 * 查询所有织机配方，找到与主手物品匹配的配方
                 */
                List<LoomRecipe> recipes = new ArrayList<>(Helpers.getRecipes(world.getRecipeManager(), TFCRecipeTypes.LOOM).values());
                List<LoomRecipe> matchingRecipes = new ArrayList<>();
                for (LoomRecipe recipe : recipes) {
                    ItemStack resultStack = recipe.getResultItem(world.registryAccess());
                    if (ItemStack.isSameItem(resultStack, mainHand)) {
                        matchingRecipes.add(recipe);
                    }
                }
                if (matchingRecipes.isEmpty()) {
                    this.state = STATE_FIND_RECIPE;
                    return;
                }

                this.currentRecipe = matchingRecipes.get(0);

                /**
                 * 检查织机已有原料，计算需要补充的数量
                 */
                ItemStack slotRecipe = inventory.getStackInSlot(SLOT_RECIPE);
                int inLoom = slotRecipe.isEmpty() ? 0 : slotRecipe.getCount();
                int needed = this.currentRecipe.getInputCount() - inLoom;

                /**
                 * 根据已有原料情况决定下一步状态
                 */
                if (inLoom >= this.currentRecipe.getInputCount()) {
                    this.state = STATE_WORKING;
                } else if (needed > 0 && !hasEnoughItems(maid, this.currentRecipe, needed)) {
                    this.state = STATE_FIND_RECIPE;
                    this.currentRecipe = null;
                } else {
                    this.state = STATE_ADD_ITEM;
                }
                break;
            }
            case STATE_ADD_ITEM: {
                /**
                 * 再次检查输出槽，防止中间有新成品生成
                 */
                ItemStack slotOutput = inventory.getStackInSlot(SLOT_OUTPUT);
                if (!slotOutput.isEmpty()) {
                    this.state = STATE_COLLECT;
                    return;
                }

                ItemStack slotRecipe = inventory.getStackInSlot(SLOT_RECIPE);
                
                /**
                 * 分两种情况添加原料：
                 * 1. 原料槽是空的：添加完整配方数量的原料
                 * 2. 原料槽有部分原料：只补充不足的部分
                 */
                if (slotRecipe.isEmpty()) {
                    ItemStack toAdd = extractItems(maid, this.currentRecipe);
                    if (toAdd.isEmpty()) {
                        this.state = STATE_FIND_RECIPE;
                        this.currentRecipe = null;
                        return;
                    }
                    inventory.setStackInSlot(SLOT_RECIPE, toAdd);
                    accessor.tfcmaid$setNeedsRecipeUpdate(true);
                    accessor.tfcmaid$setProgress(0);
                    loom.markForSync();
                } else if (slotRecipe.getCount() < this.currentRecipe.getInputCount()) {
                    int needed = this.currentRecipe.getInputCount() - slotRecipe.getCount();
                    ItemStack toAdd = extractItems(maid, this.currentRecipe, needed);
                    if (!toAdd.isEmpty()) {
                        slotRecipe.grow(toAdd.getCount());
                        accessor.tfcmaid$setNeedsRecipeUpdate(true);
                        if (slotRecipe.getCount() >= this.currentRecipe.getInputCount()) {
                            accessor.tfcmaid$setProgress(0);
                        }
                        loom.markForSync();
                    } else {
                        this.state = STATE_FIND_RECIPE;
                        this.currentRecipe = null;
                        return;
                    }
                }

                /**
                 * 检查是否已经添加足够原料
                 */
                if (inventory.getStackInSlot(SLOT_RECIPE).getCount() >= this.currentRecipe.getInputCount()) {
                    accessor.tfcmaid$setNeedsRecipeUpdate(true);
                    accessor.tfcmaid$setProgress(0);
                    loom.markForSync();
                    loom.setChanged();
                    this.state = STATE_WORKING;
                }
                break;
            }
            case STATE_WORKING: {
                /**
                 * 检查输出槽是否已有成品
                 */
                ItemStack slotOutput = inventory.getStackInSlot(SLOT_OUTPUT);
                if (!slotOutput.isEmpty()) {
                    this.state = STATE_COLLECT;
                    break;
                }

                /**
                 * 检查配方是否已加载
                 */
                LoomRecipe recipe = accessor.tfcmaid$getRecipe();
                if (recipe == null) {
                    ItemStack slotRecipe = inventory.getStackInSlot(SLOT_RECIPE);
                    if (slotRecipe.getCount() < this.currentRecipe.getInputCount()) {
                        this.state = STATE_ADD_ITEM;
                    } else {
                        accessor.tfcmaid$setNeedsRecipeUpdate(true);
                        accessor.tfcmaid$setProgress(0);
                        loom.markForSync();
                        loom.setChanged();
                    }
                    break;
                }

                int progress = accessor.tfcmaid$getProgress();
                int stepCount = recipe.getStepCount();
                
                /**
                 * 检查进度是否完成
                 */
                if (progress >= stepCount) {
                    this.state = STATE_COLLECT;
                    break;
                }

                /**
                 * 操作织机推进进度
                 * 有冷却时间限制（20 tick，约1秒）
                 */
                long time = world.getGameTime() - accessor.tfcmaid$getLastPushed();
                if (time > 20) {
                    accessor.tfcmaid$setLastPushed(world.getGameTime());
                    accessor.tfcmaid$setNeedsProgressUpdate(true);
                    loom.markForSync();
                    if (world.getBlockState(this.targetPos).getBlock() instanceof net.dries007.tfc.common.blocks.wood.TFCLoomBlock) {
                        world.playSound(null, this.targetPos, net.dries007.tfc.client.TFCSounds.LOOM_WEAVE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    } else {
                        world.playSound(null, this.targetPos, net.minecraft.sounds.SoundEvents.VILLAGER_WORK_SHEPHERD, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                }
                break;
            }
            case STATE_COLLECT: {
                /**
                 * 1. 收取输出槽的成品
                 */
                ItemStack stackInSlot = inventory.getStackInSlot(SLOT_OUTPUT);
                if (!stackInSlot.isEmpty()) {
                    ItemStack extracted = stackInSlot.copy();
                    inventory.setStackInSlot(SLOT_OUTPUT, ItemStack.EMPTY);
                    ItemsUtil.giveItemToMaid(maid, extracted);
                    accessor.tfcmaid$setProgress(0);
                    loom.markForSync();
                }

                /**
                 * 2. 返还原料槽剩余的原料
                 */
                ItemStack stackInRecipeSlot = inventory.getStackInSlot(SLOT_RECIPE);
                if (!stackInRecipeSlot.isEmpty()) {
                    ItemsUtil.giveItemToMaid(maid, stackInRecipeSlot.copy());
                    inventory.setStackInSlot(SLOT_RECIPE, ItemStack.EMPTY);
                    accessor.tfcmaid$setProgress(0);
                    accessor.tfcmaid$setNeedsRecipeUpdate(true);
                    loom.markForSync();
                }

                /**
                 * 3. 回到初始状态，准备下一次循环
                 */
                this.state = STATE_FIND_RECIPE;
                this.currentRecipe = null;
                break;
            }
        }
    }

    /**
     * 通过反射获取织机的 inventory
     *
     * @param loom 织机方块实体
     * @return 织机的物品处理器
     */
    private IItemHandlerModifiable getLoomInventory(LoomBlockEntity loom) {
        if (inventoryField == null) {
            LOGGER.error("MaidLoomWorkTask: inventoryField is null");
            return null;
        }
        try {
            return (IItemHandlerModifiable) inventoryField.get(loom);
        } catch (Exception e) {
            LOGGER.error("MaidLoomWorkTask: Failed to get loom inventory", e);
            return null;
        }
    }

    /**
     * 获取状态的可读名称
     *
     * @param state 状态码
     * @return 状态名称
     */
    private String getStateName(int state) {
        return switch (state) {
            case STATE_FIND_RECIPE -> "FIND_RECIPE";
            case STATE_ADD_ITEM -> "ADD_ITEM";
            case STATE_WORKING -> "WORKING";
            case STATE_COLLECT -> "COLLECT";
            default -> "UNKNOWN";
        };
    }

    /**
     * 检查女仆背包是否有足够的配方原料
     *
     * @param maid 女仆实体
     * @param recipe 要检查的配方
     * @return true表示有足够的原料
     */
    private boolean hasEnoughItems(EntityMaid maid, LoomRecipe recipe) {
        return hasEnoughItems(maid, recipe, recipe.getInputCount());
    }

    /**
     * 检查女仆背包是否有足够的原料（指定数量）
     *
     * @param maid 女仆实体
     * @param recipe 要检查的配方
     * @param needed 需要的数量
     * @return true表示有足够的原料
     */
    private boolean hasEnoughItems(EntityMaid maid, LoomRecipe recipe, int needed) {
        int count = 0;
        for (int i = 0; i < maid.getMaidInv().getSlots(); i++) {
            ItemStack stack = maid.getMaidInv().getStackInSlot(i);
            if (recipe.getItemStackIngredient().ingredient().test(stack)) {
                count += stack.getCount();
            }
        }
        return count >= needed;
    }

    /**
     * 从女仆背包提取完整配方数量的原料
     *
     * @param maid 女仆实体
     * @param recipe 配方
     * @return 提取的物品
     */
    private ItemStack extractItems(EntityMaid maid, LoomRecipe recipe) {
        return extractItems(maid, recipe, recipe.getInputCount());
    }

    /**
     * 从女仆背包提取指定数量的原料
     *
     * @param maid 女仆实体
     * @param recipe 配方
     * @param needed 需要提取的数量
     * @return 提取的物品
     */
    private ItemStack extractItems(EntityMaid maid, LoomRecipe recipe, int needed) {
        ItemStack result = ItemStack.EMPTY;
        for (int i = 0; i < maid.getMaidInv().getSlots() && needed > 0; i++) {
            ItemStack stack = maid.getMaidInv().getStackInSlot(i);
            if (recipe.getItemStackIngredient().ingredient().test(stack)) {
                int take = Math.min(stack.getCount(), needed);
                if (result.isEmpty()) {
                    result = stack.copy();
                    result.setCount(take);
                } else if (ItemStack.isSameItemSameTags(result, stack)) {
                    result.grow(take);
                }
                stack.shrink(take);
                needed -= take;
            }
        }
        return result;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, EntityMaid maid, long gameTime) {
        if (this.targetPos == null) {
            return false;
        }
        BlockEntity be = world.getBlockEntity(this.targetPos);
        if (!(be instanceof LoomBlockEntity)) {
            return false;
        }
        double distSqr = maid.distanceToSqr(this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D, this.targetPos.getZ() + 0.5D);
        if (distSqr > 16.0D) { // 4格范围（4^2=16）
            return false;
        }
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).isPresent();
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTime) {
        this.state = STATE_FIND_RECIPE;
        this.currentRecipe = null;
        this.targetPos = null;
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}
