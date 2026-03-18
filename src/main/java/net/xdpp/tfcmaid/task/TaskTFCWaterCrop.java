package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFarmMoveTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFarmPlantTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.dries007.tfc.common.blocks.crop.Crop;
import net.dries007.tfc.common.blocks.crop.CropBlock;
import net.dries007.tfc.common.blocks.crop.DeadCropBlock;
import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Fertilizer;
import net.dries007.tfc.util.climate.ClimateRange;
import net.xdpp.tfcmaid.Tfcmaid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * TFC水生种植任务
 * 专门处理需要在水中种植的作物，目前只有水稻
 * 需要女仆能游泳，所以重写了移动任务让女仆愿意下水
 */
public class TaskTFCWaterCrop extends TaskTFCFarmBase {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_water_crop");

    public TaskTFCWaterCrop() {
        super();
        applicableCrops = new ArrayList<>();
        applicableCrops.add(Crop.RICE);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * 检查女仆是否有必要的工具（主手有锄头）
     */
    private boolean hasRequiredTools(EntityMaid maid) {
        return hasHoe(maid);
    }

    /**
     * 检查女仆背包里是否有适用的种子
     */
    private boolean hasApplicableSeed(EntityMaid maid) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isSeed(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回任务条件描述，在UI中显示
     */
    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(
                Pair.of("has_hoe", this::hasRequiredTools),
                Pair.of("has_seed", this::hasApplicableSeed)
        );
    }

    @Override
    public ItemStack getIcon() {
        return TFCItems.CROP_SEEDS.get(Crop.RICE).get().getDefaultInstance();
    }

    /**
     * 判断某个物品是否是这个任务适用的种子
     */
    @Override
    public boolean isSeed(ItemStack stack) {
        for (Crop crop : applicableCrops) {
            if (stack.is(TFCItems.CROP_SEEDS.get(crop).get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断某个位置的作物是否可以收获
     * 可以收获的情况：
     * 1. 作物是枯萎的
     * 2. 作物已经完全成熟
     */
    @Override
    public boolean canHarvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        Block block = cropState.getBlock();
        if (block instanceof DeadCropBlock) {
            return true;
        }
        if (!(block instanceof CropBlock)) {
            return false;
        }
        CropBlock cropBlock = (CropBlock) block;
        return cropBlock.isMaxAge(cropState);
    }

    /**
     * 收获指定位置的作物
     * 如果是枯萎作物或者普通成熟作物，直接破坏方块
     */
    @Override
    public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        Block block = cropState.getBlock();
        if (block instanceof DeadCropBlock) {
            maid.destroyBlock(cropPos);
            return;
        }
        if (block instanceof CropBlock) {
            maid.destroyBlock(cropPos);
        }
    }

    /**
     * 判断某个位置是否可以种植水稻
     * 种植条件：
     * 1. 主手有锄头
     * 2. 下方是耕地
     * 3. 耕地上方一格是水（水稻要种在水里）
     * 4. 水面上方一格是空气（不能淹太深）
     * 5. 气候条件符合
     */
    @Override
    public boolean canPlant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        if (!hasHoe(maid)) {
            return false;
        }
        if (!(baseState.getBlock() instanceof FarmlandBlock)) {
            return false;
        }
        BlockPos cropPos = basePos.above();
        BlockState cropState = maid.level().getBlockState(cropPos);

        if (cropState.getBlock() instanceof CropBlock) {
            return false;
        }

        if (!cropState.getFluidState().is(Fluids.WATER)) {
            return false;
        }

        BlockPos aboveCropPos = cropPos.above();
        BlockState aboveCropState = maid.level().getBlockState(aboveCropPos);
        if (aboveCropState.getFluidState().is(Fluids.WATER)) {
            return false;
        }

        Optional<Crop> cropOpt = getCropFromSeed(seed);
        if (cropOpt.isEmpty()) {
            return false;
        }
        Crop crop = cropOpt.get();
        ClimateRange climateRange = crop.getClimateRange().get();
        if (!checkClimateConditions(maid.level(), basePos, climateRange)) {
            return false;
        }
        return true;
    }

    /**
     * 种植水稻
     * 步骤：
     * 1. 如果需要，先给耕地施肥
     * 2. 在水里放置稻种
     */
    @Override
    public ItemStack plant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        Optional<Crop> cropOpt = getCropFromSeed(seed);
        if (cropOpt.isPresent()) {
            Crop crop = cropOpt.get();
            FarmlandBlockEntity.NutrientType primaryNutrient = crop.getPrimaryNutrient();
            Optional<FarmlandBlockEntity> farmlandOpt = getFarmlandEntity(maid.level(), basePos);
            if (farmlandOpt.isPresent()) {
                FarmlandBlockEntity farmland = farmlandOpt.get();
                if (shouldFertilizeCrop(farmland, primaryNutrient)) {
                    Optional<Fertilizer> fertilizerOpt = findBestFertilizer(maid, farmland, primaryNutrient);
                    if (fertilizerOpt.isPresent()) {
                        applyFertilizer(maid, basePos, fertilizerOpt.get());
                    }
                }
            }
            BlockPos cropPos = basePos.above();
            maid.placeItemBlock(cropPos, seed);
        }
        return seed;
    }

    /**
     * 从种子物品栈中获取对应的作物类型
     */
    private Optional<Crop> getCropFromSeed(ItemStack stack) {
        for (Crop crop : applicableCrops) {
            if (stack.is(TFCItems.CROP_SEEDS.get(crop).get())) {
                return Optional.of(crop);
            }
        }
        return Optional.empty();
    }

    /**
     * 自定义的水生作物移动任务
     * 让女仆愿意游泳来种植水稻
     */
    private static class WaterFarmMoveTask extends MaidFarmMoveTask {
        public WaterFarmMoveTask(TaskTFCWaterCrop task, float movementSpeed) {
            super(task, movementSpeed);
        }

        @Override
        protected void start(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
            entityIn.getSwimManager().setWantToSwim(true);
            super.start(worldIn, entityIn, gameTimeIn);
        }
    }

    /**
     * 创建女仆的AI行为任务
     * 用我们自定义的WaterFarmMoveTask代替普通的移动任务
     */
    @Override
    public List<Pair<Integer, net.minecraft.world.entity.ai.behavior.BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        WaterFarmMoveTask waterFarmMoveTask = new WaterFarmMoveTask(this, 0.6f);
        MaidFarmPlantTask maidFarmPlantTask = new MaidFarmPlantTask(this);
        return Lists.newArrayList(Pair.of(5, waterFarmMoveTask), Pair.of(6, maidFarmPlantTask));
    }
}
