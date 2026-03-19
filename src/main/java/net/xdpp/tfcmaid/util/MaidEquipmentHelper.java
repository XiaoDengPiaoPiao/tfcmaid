package net.xdpp.tfcmaid.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 女仆装备物品的工具类
 * <p>
 * 封装了从女仆背包中查找并装备物品的通用逻辑
 */
public class MaidEquipmentHelper {

    /**
     * 从女仆背包中查找并装备符合条件的物品
     * 查找顺序：主手 -> 背包
     * 如果主手已经有符合条件的物品，直接返回 true
     * 否则从背包中查找，找到后装备到主手
     *
     * @param maid 女仆实体
     * @param predicate 物品是否符合条件的谓词
     * @param count 要提取的物品数量
     * @return 是否成功装备了物品
     */
    public static boolean findAndEquipItem(EntityMaid maid, Predicate<ItemStack> predicate, int count) {
        ItemStack mainHand = maid.getMainHandItem();
        
        if (!mainHand.isEmpty() && predicate.test(mainHand)) {
            return true;
        }

        IItemHandler backpack = maid.getAvailableBackpackInv();
        for (int i = 0; i < backpack.getSlots(); i++) {
            ItemStack stack = backpack.getStackInSlot(i);
            if (!stack.isEmpty() && predicate.test(stack)) {
                ItemStack extracted = backpack.extractItem(i, count, false);
                if (!mainHand.isEmpty()) {
                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, mainHand, false);
                    if (!remaining.isEmpty()) {
                        backpack.insertItem(i, extracted, false);
                        return false;
                    }
                }
                maid.setItemInHand(InteractionHand.MAIN_HAND, extracted);
                return true;
            }
        }

        return false;
    }

    /**
     * 从女仆背包中查找并装备符合条件的物品（默认提取1个）
     *
     * @param maid 女仆实体
     * @param predicate 物品是否符合条件的谓词
     * @return 是否成功装备了物品
     */
    public static boolean findAndEquipItem(EntityMaid maid, Predicate<ItemStack> predicate) {
        return findAndEquipItem(maid, predicate, 1);
    }

    /**
     * 从女仆背包中查找并装备符合条件的物品，并且对物品进行额外验证
     * <p>
     * 这个方法适用于需要对单个物品进行额外处理的场景，比如检查容器是否还能接受更多液体
     *
     * @param maid 女仆实体
     * @param stackTransformer 从原物品栈中提取单个物品并进行验证的函数
     *                         函数返回 null 表示验证失败，返回 ItemStack 表示验证成功
     * @return 是否成功装备了物品
     */
    public static boolean findAndEquipItemWithValidation(EntityMaid maid, Function<ItemStack, ItemStack> stackTransformer) {
        ItemStack mainHand = maid.getMainHandItem();
        
        if (!mainHand.isEmpty()) {
            ItemStack validatedStack = stackTransformer.apply(mainHand);
            if (validatedStack != null) {
                return true;
            }
        }

        IItemHandler backpack = maid.getAvailableBackpackInv();
        for (int i = 0; i < backpack.getSlots(); i++) {
            ItemStack stack = backpack.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStack validatedStack = stackTransformer.apply(stack);
                if (validatedStack != null) {
                    ItemStack extracted = backpack.extractItem(i, 1, false);
                    if (!mainHand.isEmpty()) {
                        ItemStack remaining = ItemHandlerHelper.insertItemStacked(backpack, mainHand, false);
                        if (!remaining.isEmpty()) {
                            backpack.insertItem(i, extracted, false);
                            return false;
                        }
                    }
                    maid.setItemInHand(InteractionHand.MAIN_HAND, validatedStack);
                    return true;
                }
            }
        }

        return false;
    }
}
