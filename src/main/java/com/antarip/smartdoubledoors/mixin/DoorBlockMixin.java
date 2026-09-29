package com.antarip.smartdoubledoors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
public abstract class DoorBlockMixin {

    @Unique
    private static final ThreadLocal<Boolean> smartdoubledoors$syncing = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void smartdoubledoors$syncNeighborDoor(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir) {

        // Avoid recursion and only run server-side
        if (level.isClientSide() || smartdoubledoors$syncing.get()) {
            return;
        }

        // Re-read the state after vanilla toggled it
        BlockState clickedState = level.getBlockState(pos);
        if (!(clickedState.getBlock() instanceof DoorBlock)) {
            return;
        }

        boolean isOpen = clickedState.getValue(DoorBlock.OPEN);
        Direction facing = clickedState.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = clickedState.getValue(DoorBlock.HINGE);
        DoubleBlockHalf half = clickedState.getValue(DoorBlock.HALF);

        // Determine offset to the adjacent door based on hinge side and facing
        Direction neighborDir = hinge == DoorHingeSide.LEFT
                ? facing.getClockWise()
                : facing.getCounterClockWise();

        BlockPos neighborPos = pos.relative(neighborDir);
        BlockState neighborState = level.getBlockState(neighborPos);

        // Check: is the neighbor the same type of door, opposite hinge, same facing, same half?
        if (!(neighborState.getBlock() instanceof DoorBlock)) {
            return;
        }
        if (neighborState.getBlock() != clickedState.getBlock()) {
            return;
        }
        if (neighborState.getValue(DoorBlock.HINGE) == hinge) {
            return; // Same hinge side — not a matching double door
        }
        if (neighborState.getValue(DoorBlock.FACING) != facing) {
            return;
        }
        if (neighborState.getValue(DoorBlock.HALF) != half) {
            return;
        }

        // Sync the neighbor door's open state
        smartdoubledoors$syncing.set(Boolean.TRUE);
        try {
            level.setBlock(neighborPos, neighborState.setValue(DoorBlock.OPEN, isOpen), 10);
            // Play the door sound for the neighbor too
            // Flag 10 = NOTIFY_NEIGHBORS | NOTIFY_LISTENERS (no block update cascade)
        } finally {
            smartdoubledoors$syncing.set(Boolean.FALSE);
        }
    }
}
