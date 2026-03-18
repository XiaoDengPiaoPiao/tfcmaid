package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.dries007.tfc.common.blocks.crop.Crop;
import net.dries007.tfc.common.blocks.crop.CropBlock;
import net.dries007.tfc.common.blocks.crop.DeadCropBlock;
import net.dries007.tfc.common.blocks.crop.PickableCropBlock;
import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.common.blockentities.CropBlockEntity;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Fertilizer;
import net.dries007.tfc.util.climate.ClimateRange;
import net.xdpp.tfcmaid.Tfcmaid;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;

/**
 * TFC可采摘种植任务
 * 专门处理可采摘作物（不破坏植株就能收获的作物）
 * 目前包括：红甜椒和黄甜椒
 *
 * 收获后作物会重新生长，不会被破坏
 */
public class TaskTFCPickableCrop extends TaskTFCFarmBase {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_pickable_crop");

    public TaskTFCPickableCrop() {
        super();
        applicableCrops = new ArrayList<>();
        applicableCrops.add(Crop.RED_BELL_PEPPER);
        applicableCrops.add(Crop.YELLOW_BELL_PEPPER);
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
        return TFCItems.CROP_SEEDS.get(Crop.RED_BELL_PEPPER).get().getDefaultInstance();
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
     * 2. 如果是可采摘作物，年龄在倒数第二阶段或更高即可收获
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
        if (block instanceof PickableCropBlock pickableCrop) {
            int age = cropState.getValue(pickableCrop.getAgeProperty());
            return age >= pickableCrop.getMaxAge() - 1;
        }
        CropBlock cropBlock = (CropBlock) block;
        return cropBlock.isMaxAge(cropState);
    }

    /**
     * 收获指定位置的作物
     *
     * 如果是可采摘作物：
     * 1. 根据作物当前年龄获取对应果实
     * 2. 根据产量和随机数计算收获数量
     * 3. 重置作物生长值和产量
     * 4. 更新作物方块年龄，让作物看起来重新生长
     * 5. 把收获给女仆
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
        if (block instanceof PickableCropBlock pickableCrop) {
            Level level = maid.level();
            Optional<CropBlockEntity> cropOpt = getCropEntity(level, cropPos);
            if (cropOpt.isPresent()) {
                CropBlockEntity crop = cropOpt.get();
                float yield = crop.getYield();
                int age = cropState.getValue(pickableCrop.getAgeProperty());
                RandomSource random = level.random;

                ItemStack result;
                float newGrowth;
                if (age == pickableCrop.getMaxAge() - 1 && pickableCrop.getFirstFruit() != null) {
                    newGrowth = 0.4f + random.nextFloat() * 0.1f;
                    crop.setGrowth(newGrowth);
                    crop.setYield(0f);
                    result = yieldItemStack(pickableCrop.getFirstFruit(), yield, random);
                } else {
                    newGrowth = 0.5f + random.nextFloat() * 0.1f;
                    crop.setGrowth(newGrowth);
                    crop.setYield(0f);
                    result = yieldItemStack(pickableCrop.getSecondFruit(), yield, random);
                }

                int newAge = newGrowth == 1 ? pickableCrop.getMaxAge() : (int) (newGrowth * pickableCrop.getMaxAge());
                BlockState newState = cropState.setValue(pickableCrop.getAgeProperty(), newAge);
                level.setBlockAndUpdate(cropPos, newState);

                ItemsUtil.giveItemToMaid(maid, result);
                maid.swing(InteractionHand.MAIN_HAND);
            }
        } else if (block instanceof CropBlock) {
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
        return true;
    }

    /**
     * 种植作物
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
     * 根据作物产量和随机数计算实际收获数量
     * 公式：lerp(产量, 1个, 5个) + 0或1个随机奖励
     */
    private ItemStack yieldItemStack(Item item, float yield, RandomSource random) {
        return new ItemStack(item, (int) Math.floor(Mth.lerp(yield, 1f, 5f) + random.nextInt(2)));
    }
}
