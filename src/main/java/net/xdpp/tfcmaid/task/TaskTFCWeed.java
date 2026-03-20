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
import net.xdpp.tfcmaid.config.WeedConfigManager;

public class TaskTFCWeed implements IFarmTask {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_weed");

    public static final TagKey<Block> TFC_PLANTS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse("tfc:plants"));
    public static final TagKey<Block> TFC_WILD_CROPS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse("tfc:wild_crops"));

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.GRASS.getDefaultInstance();
    }

    @Override
    public boolean isSeed(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canHarvest(EntityMaid maid, BlockPos cropPos, BlockState cropState) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(cropState.getBlock());

        if (WeedConfigManager.isBlockBlacklisted(blockId.toString())) {
            return false;
        }

        if (cropState.is(TFC_PLANTS)) {
            return true;
        }

        if (cropState.is(TFC_WILD_CROPS)) {
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
        return "Clear nearby TFC plants, wild crops, and weeds.";
    }
}
