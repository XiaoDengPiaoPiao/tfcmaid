package net.xdpp.tfcmaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.task.IFeedTask;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFeedOwner;
import net.dries007.tfc.common.capabilities.Capabilities;
import net.dries007.tfc.common.capabilities.food.TFCFoodData;
import net.dries007.tfc.util.Drinkable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 女仆喂食任务Mixin，添加TFC模组的口渴值喝⽔支持
 */
@Mixin(value = TaskFeedOwner.class, remap = false)
public abstract class TaskFeedOwnerMixin {
    /**
     * 注入到isFood方法前，判断物品是否可以作为饮品给玩家喝
     * @param stack 待判断的物品栈
     * @param owner 玩家
     * @param cir 回调返回值
     */
    @Inject(method = "isFood(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void beforeIsFood(ItemStack stack, Player owner, CallbackInfoReturnable<Boolean> cir) {
        // 只处理TFC的食物数据
        if (!(owner.getFoodData() instanceof TFCFoodData tfcFoodData)) {
            return;
        }
        // 口渴值已满时不需要喝水
        if (tfcFoodData.getThirst() >= TFCFoodData.MAX_THIRST) {
            return;
        }
        // 如果物品是可饮用的，标记为可以喂食
        if (canDrinkFrom(stack)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 注入到getPriority方法前，根据口渴值设置饮品的喂食优先级
     * @param stack 待判断的物品栈
     * @param owner 玩家
     * @param cir 回调返回值
     */
    @Inject(method = "getPriority(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Lcom/github/tartaricacid/touhoulittlemaid/api/task/IFeedTask$Priority;", at = @At("HEAD"), cancellable = true, remap = false)
    private void beforeGetPriority(ItemStack stack, Player owner, CallbackInfoReturnable<IFeedTask.Priority> cir) {
        // 只处理TFC的食物数据
        if (!(owner.getFoodData() instanceof TFCFoodData tfcFoodData)) {
            return;
        }
        // 不是可饮用物品直接返回
        if (!canDrinkFrom(stack)) {
            return;
        }
        float thirst = tfcFoodData.getThirst();
        // 口渴值低于25时设置为高优先级
        if (thirst < 25f) {
            cir.setReturnValue(IFeedTask.Priority.HIGH);
        } 
        // 口渴值在25-50之间时设置为低优先级
        else if (thirst < 50f) {
            cir.setReturnValue(IFeedTask.Priority.LOW);
        }
    }

    /**
     * 判断物品是否可以饮用
     * @param stack 待判断的物品栈
     * @return 是否可以饮用
     */
    private boolean canDrinkFrom(ItemStack stack) {
        return stack.getCapability(Capabilities.FLUID_ITEM).resolve().map(handler -> {
            // 遍历物品的所有流体槽
            for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack fluidStack = handler.getFluidInTank(i);
                // 如果流体不为空且是可饮用的，返回true
                if (!fluidStack.isEmpty() && Drinkable.get(fluidStack.getFluid()) != null) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }
}

