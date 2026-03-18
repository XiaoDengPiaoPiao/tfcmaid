package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.dries007.tfc.common.blocks.crop.Crop;
import net.dries007.tfc.common.blocks.crop.CropBlock;
import net.dries007.tfc.common.blocks.crop.DeadCropBlock;
import net.dries007.tfc.common.blocks.crop.ClimbingCropBlock;
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
 * TFC普通种植任务
 * 处理除了水生、瓜类、可采摘作物之外的所有普通作物
 * 包括需要支架的攀爬类作物(番茄)
 */
public class TaskTFCNormalCrop extends TaskTFCFarmBase {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_normal_crop");

    public TaskTFCNormalCrop() {
        super();
        applicableCrops = new ArrayList<>();
        for (Crop crop : Crop.values()) {
            if (crop != Crop.RICE &&
                crop != Crop.PUMPKIN &&
                crop != Crop.MELON &&
                crop != Crop.RED_BELL_PEPPER &&
                crop != Crop.YELLOW_BELL_PEPPER) {
                applicableCrops.add(crop);
            }
        }
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
        return TFCItems.CROP_SEEDS.get(Crop.WHEAT).get().getDefaultInstance();
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
     * 判断某个位置是否可以种植
     * 种植条件：
     * 1. 主手有锄头
     * 2. 下方是耕地
     * 3. 上方是空气或可替换的方块（没有水）
     * 4. 气候条件符合
     * 5. 如果是攀爬类作物，女仆背包里要有木棍
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
        if (!cropState.canBeReplaced() || cropState.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)) {
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
        if (isClimbingCrop(crop)) {
            if (!hasStick(maid)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 种植作物
     * 步骤：
     * 1. 如果需要，先给耕地施肥
     * 2. 放置种子
     * 3. 如果是攀爬类作物，放木棍做支架
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
            if (isClimbingCrop(crop)) {
                placeStick(maid, cropPos);
            }
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
     * 判断某个作物是否是攀爬类作物（需要支架）
     * 目前包括：四季豆和番茄
     */
    private boolean isClimbingCrop(Crop crop) {
        return crop == Crop.GREEN_BEAN || crop == Crop.TOMATO;
    }

    /**
     * 在作物旁边放木棍做支架
     * 会修改作物方块的状态，设置STICK为true，并在上方放置作物的上半部分
     */
    private void placeStick(EntityMaid maid, BlockPos cropPos) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.is(Tags.Items.RODS_WOODEN)) {
                BlockPos posAbove = cropPos.above();
                if (maid.level().isEmptyBlock(posAbove)) {
                    BlockState cropState = maid.level().getBlockState(cropPos);
                    if (cropState.getBlock() instanceof ClimbingCropBlock) {
                        cropState = cropState.setValue(ClimbingCropBlock.STICK, true);
                        maid.level().setBlock(cropPos, cropState, 3);
                        maid.level().setBlock(posAbove, cropState.setValue(ClimbingCropBlock.PART, ClimbingCropBlock.Part.TOP), 3);
                        stack.shrink(1);
                        maid.swing(InteractionHand.MAIN_HAND);
                    }
                }
                break;
            }
        }
    }
}
