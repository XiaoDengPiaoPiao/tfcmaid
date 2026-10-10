package net.xdpp.tfcmaid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.xdpp.tfcmaid.Tfcmaid;
import net.xdpp.tfcmaid.util.FuelTransfer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class TaskTFCRefuel implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.parse(Tfcmaid.MODID + ":tfc_refuel");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.CHARCOAL.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public boolean workPointTask(EntityMaid maid) {
        return true;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return List.of(Pair.of(5, new Move()), Pair.of(6, new Work()));
    }

    @Override
    public String getMaidActionSummary() {
        return "Refuel lit TFC firepits, grills, pots and charcoal forges using backpack fuel.";
    }

    private static class Move extends MaidMoveToBlockTask {
        private BlockPos standingPos;

        Move() {
            super(0.6f, 3);
        }

        @Override
        protected void start(ServerLevel level, EntityMaid maid, long time) {
            standingPos = null;
            searchForDestination(level, maid);
            if (standingPos != null && maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())) {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(standingPos, 0.6f, 0));
            }
        }

        @Override
        protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos) {
            return level.hasChunkAt(pos) && FuelTransfer.transfer(level.getBlockEntity(pos), maid.getAvailableBackpackInv(), true);
        }

        @Override
        protected boolean checkPathReach(EntityMaid maid, MaidPathFindingBFS paths, BlockPos device) {
            var level = maid.level();
            // Walk beside the device rather than onto the burning block.
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int height = 0; height <= 1; height++) {
                    BlockPos stand = device.relative(side).above(height);
                    if (!maid.isWithinRestriction(stand) || !level.hasChunkAt(stand)
                            || !level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
                            || !level.getBlockState(stand.above()).getCollisionShape(level, stand).isEmpty()
                            || !paths.canPathReach(stand)) {
                        continue;
                    }
                    standingPos = stand;
                    return true;
                }
            }
            return false;
        }
    }

    private static class Work extends Behavior<EntityMaid> {
        private BlockPos target;
        private long nextFuelTick;

        Work() {
            super(Map.of(InitEntities.TARGET_POS.get(), MemoryStatus.VALUE_PRESENT), 200);
        }

        @Override
        protected void start(ServerLevel level, EntityMaid maid, long time) {
            target = maid.getBrain().getMemory(InitEntities.TARGET_POS.get())
                    .map(tracker -> tracker.currentBlockPosition().immutable()).orElse(null);
            nextFuelTick = time;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, EntityMaid maid, long time) {
            return target != null && level.hasChunkAt(target) && maid.isWithinRestriction(target)
                    && FuelTransfer.transfer(level.getBlockEntity(target), maid.getAvailableBackpackInv(), true);
        }

        @Override
        protected void tick(ServerLevel level, EntityMaid maid, long time) {
            if (target == null || time < nextFuelTick || !target.closerToCenterThan(maid.position(), 3)) {
                return;
            }
            nextFuelTick = time + 20;
            var hit = level.clip(new ClipContext(maid.getEyePosition(), Vec3.atCenterOf(target),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
            if (!hit.getBlockPos().equals(target)) {
                return;
            }
            maid.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            FuelTransfer.transfer(level.getBlockEntity(target), maid.getAvailableBackpackInv(), false);
        }

        @Override
        protected void stop(ServerLevel level, EntityMaid maid, long time) {
            target = null;
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }
}
