package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.dries007.tfc.common.items.EmptyPanItem;
import net.dries007.tfc.common.items.PanItem;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Pannable;
import net.xdpp.tfcmaid.Tfcmaid;
import net.xdpp.tfcmaid.behavior.MaidPanningTask;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class TaskTFCPanning implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_panning");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        try {
            return TFCItems.EMPTY_PAN.get().getDefaultInstance();
        } catch (Exception e) {
            return net.minecraft.world.item.Items.IRON_SHOVEL.getDefaultInstance();
        }
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_IDLE.get(), 0.15f);
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return Lists.newArrayList(Pair.of(5, new MaidPanningTask(0.6f)));
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(
                Pair.of("has_empty_pan", this::hasEmptyPan),
                Pair.of("has_pannable_in_inventory", this::hasPannableItemOrFilledPan)
        );
    }

    @Override
    public boolean workPointTask(EntityMaid maid) {
        return true;
    }

    private boolean hasEmptyPan(EntityMaid maid) {
        return maid.getMainHandItem().getItem() instanceof EmptyPanItem;
    }

    private boolean hasPannableItemOrFilledPan(EntityMaid maid) {
        if (maid.getMainHandItem().getItem() instanceof PanItem) {
            return true;
        }
        for (int i = 0; i < maid.getMaidInv().getSlots(); i++) {
            ItemStack stack = maid.getMaidInv().getStackInSlot(i);
            if (stack.getItem() instanceof PanItem) {
                return true;
            }
            if (stack.getItem() instanceof BlockItem blockItem) {
                BlockState state = blockItem.getBlock().defaultBlockState();
                if (Pannable.get(state) != null) {
                    return true;
                }
            }
        }
        return false;
    }
}
