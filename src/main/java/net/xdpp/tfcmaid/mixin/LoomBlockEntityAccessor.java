package net.xdpp.tfcmaid.mixin;

import net.dries007.tfc.common.blockentities.LoomBlockEntity;
import net.dries007.tfc.common.recipes.LoomRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * LoomBlockEntity 的 Accessor Mixin 接口
 * 用于突破访问限制，访问 LoomBlockEntity 的私有/受保护字段
 * 这些字段在 TFC 源码中是私有的，无法直接访问
 * 通过这个 Mixin 接口，我们可以：
 * 1. 获取/设置 lastPushed（最后操作时间戳，用于防止过快操作）
 * 2. 获取/设置 needsProgressUpdate（是否需要更新进度）
 * 3. 获取/设置 needsRecipeUpdate（是否需要更新配方）
 * 4. 获取当前使用的配方（recipe）
 * 5. 获取/设置当前进度（progress）
 */
@Mixin(value = LoomBlockEntity.class, remap = false)
public interface LoomBlockEntityAccessor {
    /**
     * 获取最后操作时间戳
     *
     * @return 最后操作的游戏时间戳
     */
    @Accessor("lastPushed")
    long tfcmaid$getLastPushed();

    /**
     * 设置最后操作时间戳
     *
     * @param lastPushed 要设置的游戏时间戳
     */
    @Accessor("lastPushed")
    void tfcmaid$setLastPushed(long lastPushed);

    /**
     * 获取是否需要更新进度标志
     *
     * @return true表示需要更新进度
     */
    @Accessor("needsProgressUpdate")
    boolean tfcmaid$getNeedsProgressUpdate();

    /**
     * 设置是否需要更新进度标志
     *
     * @param needsProgressUpdate 要设置的标志值
     */
    @Accessor("needsProgressUpdate")
    void tfcmaid$setNeedsProgressUpdate(boolean needsProgressUpdate);

    /**
     * 获取是否需要更新配方标志
     *
     * @return true表示需要更新配方
     */
    @Accessor("needsRecipeUpdate")
    boolean tfcmaid$getNeedsRecipeUpdate();

    /**
     * 设置是否需要更新配方标志
     *
     * @param needsRecipeUpdate 要设置的标志值
     */
    @Accessor("needsRecipeUpdate")
    void tfcmaid$setNeedsRecipeUpdate(boolean needsRecipeUpdate);

    /**
     * 获取当前织机使用的配方
     *
     * @return 当前配方对象
     */
    @Accessor("recipe")
    LoomRecipe tfcmaid$getRecipe();

    /**
     * 获取当前织机进度
     *
     * @return 当前进度值（0到配方步数之间）
     */
    @Accessor("progress")
    int tfcmaid$getProgress();

    /**
     * 设置当前织机进度
     *
     * @param progress 要设置的进度值
     */
    @Accessor("progress")
    void tfcmaid$setProgress(int progress);
}
