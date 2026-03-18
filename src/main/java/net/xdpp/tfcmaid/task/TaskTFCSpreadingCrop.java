package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.dries007.tfc.common.blocks.crop.Crop;
import net.dries007.tfc.common.blocks.crop.CropBlock;
import net.dries007.tfc.common.blocks.crop.DeadCropBlock;
import net.dries007.tfc.common.blocks.crop.SpreadingCropBlock;
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
 * TFC蔓延类种植任务
 * 专门处理会蔓延的作物（果实会在植株旁边长出来的作物）
 * 目前包括：南瓜和西瓜
 *
 * 这类作物需要：
 * 1. 主手有锄头
 * 2. 副手有斧子（用于收获果实）
 * 3. 种植间隔至少3格，给果实留出空间生长
 */
public class TaskTFCSpreadingCrop extends TaskTFCFarmBase {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_spreading_crop");

    public TaskTFCSpreadingCrop() {
        super();
        applicableCrops = new ArrayList<>();
        applicableCrops.add(Crop.PUMPKIN);
        applicableCrops.add(Crop.MELON);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /**
     * 检查女仆主手是否有锄头
     */
    private boolean hasHoeCondition(EntityMaid maid) {
        return hasHoe(maid);
    }

    /**
     * 检查女仆副手是否有斧子
     */
    private boolean hasAxeCondition(EntityMaid maid) {
        return hasAxe(maid);
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
                Pair.of("has_hoe", this::hasHoeCondition),
                Pair.of("has_axe", this::hasAxeCondition),
                Pair.of("has_seed", this::hasApplicableSeed)
        );
    }

    @Override
    public ItemStack getIcon() {
        return TFCItems.CROP_SEEDS.get(Crop.PUMPKIN).get().getDefaultInstance();
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
     * 2. 如果是蔓延类作物，植株旁边有果实
     * 3. 如果是普通作物，需要完全成熟
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
        if (block instanceof SpreadingCropBlock spreadingCrop) {
            return hasFruitNearby(maid.level(), cropPos, spreadingCrop.getFruit());
        }
        CropBlock cropBlock = (CropBlock) block;
        return cropBlock.isMaxAge(cropState);
    }

    /**
     * 收获指定位置的作物
     *
     * 如果是蔓延类作物：
     * 1. 检查四个方向是否有果实
     * 2. 用斧子破坏果实（保持植株继续生长）
     *
     * 如果是枯萎作物或普通作物，直接破坏方块
     */
    @Override
    public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        Block block = cropState.getBlock();
        if (block instanceof DeadCropBlock) {
            maid.destroyBlock(cropPos);
            return;
        }
        if (block instanceof SpreadingCropBlock spreadingCrop) {
            harvestFruitNearby(maid, cropPos, spreadingCrop.getFruit());
        } else if (block instanceof CropBlock) {
            maid.destroyBlock(cropPos);
        }
    }

    /**
     * 判断某个位置是否可以种植蔓延类作物
     * 种植条件：
     * 1. 主手有锄头
     * 2. 副手有斧子
     * 3. 下方是耕地
     * 4. 上方是空气或可替换的方块（没有水）
     * 5. 种植间隔符合（x和z坐标都是3的倍数，给果实留空间）
     * 6. 气候条件符合
     */
    @Override
    public boolean canPlant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        if (!hasHoe(maid)) {
            return false;
        }
        if (!hasAxe(maid)) {
            return false;
        }
        if (!(baseState.getBlock() instanceof FarmlandBlock)) {
            return false;
        }
        BlockPos cropPos = basePos.above();
        BlockState cropState = maid.level().getBlockState(cropPos);
        if (!cropState.canBeReplaced() || cropState.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)) {
            return false;
        }
        if (!checkPlantingSpacing(maid.level(), basePos)) {
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
     * 种植蔓延类作物
     * 步骤：
     * 1. 如果需要，先给耕地施肥
     * 2. 放置种子
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
     * 检查种植间隔是否符合要求
     * 蔓延类作物需要每3格种一株，给果实留出生长空间
     */
    private boolean checkPlantingSpacing(Level level, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        return (x % 3 == 0) && (z % 3 == 0);
    }

    /**
     * 检查植株附近四个水平方向是否有果实
     */
    private boolean hasFruitNearby(Level level, BlockPos pos, Block fruitBlock) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            mutable.setWithOffset(pos, d);
            BlockState state = level.getBlockState(mutable);
            if (state.is(fruitBlock)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收获植株附近四个水平方向的所有果实
     */
    private void harvestFruitNearby(EntityMaid maid, BlockPos pos, Block fruitBlock) {
        Level level = maid.level();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            mutable.setWithOffset(pos, d);
            BlockState state = level.getBlockState(mutable);
            if (state.is(fruitBlock)) {
                maid.destroyBlock(mutable);
            }
        }
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
}
