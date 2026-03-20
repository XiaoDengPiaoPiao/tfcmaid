package net.xdpp.tfcmaid.mixin;

import net.dries007.tfc.common.blockentities.QuernBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * QuernBlockEntity 的 Accessor Mixin 接口
 * 用于突破访问限制，访问 QuernBlockEntity 的私有字段
 * 通过这个 Mixin 接口，我们可以：
 * 1. 获取/设置 recipeTimer（推磨计时器，90 tick 为一个完整推磨周期）
 */
@Mixin(value = QuernBlockEntity.class, remap = false)
public interface QuernBlockEntityAccessor {
    /**
     * 获取推磨计时器
     *
     * @return 当前的推磨计时器值
     */
    @Accessor("recipeTimer")
    float tfcmaid$getRecipeTimer();

    /**
     * 设置推磨计时器
     *
     * @param recipeTimer 要设置的推磨计时器值
     */
    @Accessor("recipeTimer")
    void tfcmaid$setRecipeTimer(float recipeTimer);
}
