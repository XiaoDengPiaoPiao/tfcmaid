package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IFarmTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.Tags;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.dries007.tfc.common.blocks.crop.Crop;
import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity;
import net.dries007.tfc.common.blockentities.CropBlockEntity;
import net.dries007.tfc.util.Fertilizer;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.ClimateRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TFC女仆种植任务的基类
 * 提供所有种植任务通用的工具方法和逻辑
 * 四个具体的种植任务都继承自这个类
 */
public abstract class TaskTFCFarmBase implements IFarmTask {

    /**
     * 这个任务适用的作物列表
     * 每个具体任务都会在构造函数中添加自己适用的作物
     */
    protected List<Crop> applicableCrops = new ArrayList<>();

    protected TaskTFCFarmBase() {
    }

    /**
     * 检查某个作物是否属于这个任务的处理范围
     *
     * @param crop 要检查的作物
     * @return true表示这个任务可以处理该作物
     */
    protected boolean isCropApplicable(Crop crop) {
        return applicableCrops.contains(crop);
    }

    /**
     * 获取指定位置的耕地方块实体
     *
     * @param level 世界
     * @param pos   耕地位置（是basePos，不是作物位置）
     * @return 耕地实体的Optional
     */
    protected Optional<FarmlandBlockEntity> getFarmlandEntity(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof FarmlandBlockEntity farmland) {
            return Optional.of(farmland);
        }
        return Optional.empty();
    }

    /**
     * 获取指定位置的作物方块实体
     *
     * @param level 世界
     * @param pos   作物位置（是cropPos，不是耕地位置）
     * @return 作物实体的Optional
     */
    protected Optional<CropBlockEntity> getCropEntity(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof CropBlockEntity crop) {
            return Optional.of(crop);
        }
        return Optional.empty();
    }

    /**
     * 检查指定位置的气候条件（温度和湿度）是否符合作物要求
     *
     * @param level         世界
     * @param pos           要检查的位置（通常是耕地位置）
     * @param climateRange  作物的气候范围要求
     * @return true表示气候条件符合要求
     */
    protected boolean checkClimateConditions(Level level, BlockPos pos, ClimateRange climateRange) {
        int hydration = FarmlandBlock.getHydration(level, pos);
        float temperature = Climate.getTemperature(level, pos);
        return climateRange.checkBoth(hydration, temperature, true);
    }

    /**
     * 检查某种肥料对当前耕地和作物是否有用
     * 有用的定义：
     * 1. 能增加作物主要需要的营养
     * 2. 不会让主要营养溢出（超过100）
     *
     * @param farmland        耕地实体
     * @param fertilizer      要检查的肥料
     * @param primaryNutrient 作物主要需要的营养类型（N/P/K）
     * @return true表示这个肥料值得使用
     */
    protected boolean isFertilizerUseful(FarmlandBlockEntity farmland, Fertilizer fertilizer, FarmlandBlockEntity.NutrientType primaryNutrient) {
        float currentN = farmland.getNutrient(FarmlandBlockEntity.NutrientType.NITROGEN);
        float currentP = farmland.getNutrient(FarmlandBlockEntity.NutrientType.PHOSPHOROUS);
        float currentK = farmland.getNutrient(FarmlandBlockEntity.NutrientType.POTASSIUM);

        float addN = fertilizer.getNitrogen();
        float addP = fertilizer.getPhosphorus();
        float addK = fertilizer.getPotassium();

        boolean wouldOverflowPrimary = false;
        boolean isUsefulForPrimary = false;

        if (primaryNutrient == FarmlandBlockEntity.NutrientType.NITROGEN) {
            wouldOverflowPrimary = (currentN + addN) > 1.0f;
            isUsefulForPrimary = addN > 0 && currentN < 1.0f;
        } else if (primaryNutrient == FarmlandBlockEntity.NutrientType.PHOSPHOROUS) {
            wouldOverflowPrimary = (currentP + addP) > 1.0f;
            isUsefulForPrimary = addP > 0 && currentP < 1.0f;
        } else {
            wouldOverflowPrimary = (currentK + addK) > 1.0f;
            isUsefulForPrimary = addK > 0 && currentK < 1.0f;
        }

        return isUsefulForPrimary && !wouldOverflowPrimary;
    }

    /**
     * 在女仆背包中找到最适合当前作物和耕地的肥料
     * 选择标准：对作物主要营养贡献最大的肥料
     *
     * @param maid           女仆实体
     * @param farmland       耕地实体
     * @param primaryNutrient 作物主要需要的营养类型
     * @return 找到的最佳肥料的Optional
     */
    protected Optional<Fertilizer> findBestFertilizer(EntityMaid maid, FarmlandBlockEntity farmland, FarmlandBlockEntity.NutrientType primaryNutrient) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        Fertilizer bestFertilizer = null;
        float bestScore = -1;

        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            Fertilizer fertilizer = Fertilizer.get(stack);
            if (fertilizer != null && isFertilizerUseful(farmland, fertilizer, primaryNutrient)) {
                float score = 0;
                if (primaryNutrient == FarmlandBlockEntity.NutrientType.NITROGEN) {
                    score = fertilizer.getNitrogen();
                } else if (primaryNutrient == FarmlandBlockEntity.NutrientType.PHOSPHOROUS) {
                    score = fertilizer.getPhosphorus();
                } else {
                    score = fertilizer.getPotassium();
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestFertilizer = fertilizer;
                }
            }
        }

        return Optional.ofNullable(bestFertilizer);
    }

    /**
     * 让女仆给指定位置的耕地施肥
     * 会在女仆背包中找到对应的肥料并消耗
     *
     * @param maid         女仆实体
     * @param farmlandPos  要施肥的耕地位置
     * @param fertilizer   要使用的肥料
     */
    protected void applyFertilizer(EntityMaid maid, BlockPos farmlandPos, Fertilizer fertilizer) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            Fertilizer found = Fertilizer.get(stack);
            if (found == fertilizer && !stack.isEmpty()) {
                Optional<FarmlandBlockEntity> farmlandOpt = getFarmlandEntity(maid.level(), farmlandPos);
                if (farmlandOpt.isPresent()) {
                    FarmlandBlockEntity farmland = farmlandOpt.get();
                    farmland.addNutrients(fertilizer);
                    stack.shrink(1);
                    maid.swing(InteractionHand.MAIN_HAND);
                }
                break;
            }
        }
    }

    /**
     * 判断是否应该给作物施肥
     * 只有当作物主要营养低于70%时才施肥
     *
     * @param farmland        耕地实体
     * @param primaryNutrient 作物主要需要的营养类型
     * @return true表示应该施肥
     */
    protected boolean shouldFertilizeCrop(FarmlandBlockEntity farmland, FarmlandBlockEntity.NutrientType primaryNutrient) {
        float currentPrimary = farmland.getNutrient(primaryNutrient);
        return currentPrimary < 0.7f;
    }

    /**
     * 检查女仆主手是否持有锄头
     *
     * @param maid 女仆实体
     * @return true表示主手有锄头
     */
    protected boolean hasHoe(EntityMaid maid) {
        return maid.getMainHandItem().getItem() instanceof net.minecraft.world.item.HoeItem;
    }

    /**
     * 检查女仆副手是否持有斧子
     *
     * @param maid 女仆实体
     * @return true表示副手有斧子
     */
    protected boolean hasAxe(EntityMaid maid) {
        return maid.getOffhandItem().getItem() instanceof net.minecraft.world.item.AxeItem;
    }

    /**
     * 检查女仆背包里是否有木棍（用于攀爬类作物的支架）
     * 检查的是RODS_WOODEN标签，包括原版木棍和TFC的木棍
     *
     * @param maid 女仆实体
     * @return true表示背包里有木棍
     */
    protected boolean hasStick(EntityMaid maid) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.is(Tags.Items.RODS_WOODEN)) {
                return true;
            }
        }
        return false;
    }
}
