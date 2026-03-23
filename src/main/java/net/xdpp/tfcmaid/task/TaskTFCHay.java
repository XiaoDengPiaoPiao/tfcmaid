package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IFarmTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.plant.ShortGrassBlock;
import net.dries007.tfc.common.blocks.plant.TFCTallGrassBlock;
import net.xdpp.tfcmaid.Tfcmaid;
import net.xdpp.tfcmaid.config.WeedConfigManager;

import java.util.List;
import java.util.function.Predicate;

public class TaskTFCHay implements IFarmTask {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_hay");

    public static final TagKey<Block> TFC_PLANTS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse("tfc:plants"));
    public static final TagKey<net.minecraft.world.item.Item> TFC_SCYTHES = TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("tfc:scythes"));

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("tfc:straw"))
                .map(ItemStack::new)
                .orElse(Items.WHEAT.getDefaultInstance());
    }

    @Override
    public boolean isSeed(ItemStack stack) {
        return false;
    }

    private boolean hasRequiredTools(EntityMaid maid) {
        ItemStack mainHand = maid.getMainHandItem();
        return mainHand.getItem() instanceof HoeItem 
                || mainHand.is(TFCTags.Items.KNIVES) 
                || mainHand.is(TFC_SCYTHES);
    }

    private boolean hasInventorySpace(EntityMaid maid) {
        CombinedInvWrapper inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canHarvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        if (!hasRequiredTools(maid)) {
            return false;
        }
        
        if (!hasInventorySpace(maid)) {
            return false;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(cropState.getBlock());

        if (WeedConfigManager.isBlockBlacklisted(blockId.toString())) {
            return false;
        }

        if (!cropState.is(TFC_PLANTS)) {
            return false;
        }

        Block block = cropState.getBlock();
        return block instanceof ShortGrassBlock || block instanceof TFCTallGrassBlock;
    }

    @Override
    public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        if (maid.level() instanceof ServerLevel serverLevel) {
            if (maid.canDestroyBlock(cropPos)) {
                BlockEntity blockEntity = cropState.hasBlockEntity() ? maid.level().getBlockEntity(cropPos) : null;
                maid.dropResourcesToMaidInv(cropState, maid.level(), cropPos, blockEntity, maid, maid.getMainHandItem());
                maid.level().setBlock(cropPos, cropState.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public boolean canPlant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        return false;
    }

    @Override
    public ItemStack plant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        return seed;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(
                Pair.of("has_hoe_or_knife", this::hasRequiredTools)
        );
    }
}
