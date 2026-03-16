package net.xdpp.tfcmaid.mixin;

import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mojang.text2speech.Narrator.LOGGER;

// Mixin测试类,用于检查mixin生效状态，魂符放出女仆终端提示hello TFC_maid

@Mixin(ItemSmartSlab.class)
abstract public class SmartSlabMixin {
    @Inject(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), remap = false)
    public void useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        LOGGER.info("hello TFC_maid");
    }
}
