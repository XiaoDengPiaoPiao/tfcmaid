package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IFarmTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.xdpp.tfcmaid.Tfcmaid;

public class TaskTFCDebris implements IFarmTask {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_debris");

    public static final TagKey<Block> TFC_TWIGS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse("tfc:twigs"));
    public static final TagKey<Block> TFC_LOOSE_ROCKS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse("tfc:loose_rocks"));

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("tfc:rock/mossy_loose/conglomerate"))
                .map(ItemStack::new)
                .orElse(Items.FLINT.getDefaultInstance());
    }

    @Override
    public boolean isSeed(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canHarvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        if (cropState.is(TFC_TWIGS)) {
            return true;
        }
        if (cropState.is(TFC_LOOSE_ROCKS)) {
            return true;
        }
        return false;
    }

    @Override
    public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        maid.destroyBlock(cropPos);
    }

    @Override
    public boolean canPlant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        return false;
    }

    @Override
    public ItemStack plant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        return seed;
    }

    public String getMaidActionSummary() {
        return "Collect nearby TFC twigs and loose rocks.";
    }
}
