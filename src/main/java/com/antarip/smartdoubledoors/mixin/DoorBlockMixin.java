package com.antarip.smartdoubledoors.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
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

    // Modern Minecraft (1.20.5+ and 26.x)
    @Inject(method = "useWithoutItem", at = @At("RETURN"), require = 0)
    private void smartdoubledoors$afterUseWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue().consumesAction()) {
            smartdoubledoors$syncNeighborDoor(level, pos, player);
        }
    }

    // Classic Minecraft (1.20 - 1.20.4)
    @Inject(method = "use", at = @At("RETURN"), require = 0)
    private void smartdoubledoors$afterUse(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue().consumesAction()) {
            smartdoubledoors$syncNeighborDoor(level, pos, player);
        }
    }

    @Unique
    private static void smartdoubledoors$syncNeighborDoor(Level level, BlockPos pos, Player player) {
        if (level.isClientSide() || smartdoubledoors$syncing.get()) {
            return;
        }

        BlockState clickedState = level.getBlockState(pos);
        if (!(clickedState.getBlock() instanceof DoorBlock)) {
            return;
        }

        boolean isOpen = clickedState.getValue(DoorBlock.OPEN);
        Direction facing = clickedState.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = clickedState.getValue(DoorBlock.HINGE);
        DoubleBlockHalf half = clickedState.getValue(DoorBlock.HALF);

        // Adjacent door offset: left hinge pairs with right hinge to its clockwise direction, and vice versa
        Direction neighborDir = (hinge == DoorHingeSide.LEFT) ? facing.getClockWise() : facing.getCounterClockWise();
        BlockPos neighborPos = pos.relative(neighborDir);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (!(neighborState.getBlock() instanceof DoorBlock neighborDoor)) {
            return;
        }
        // Must be same door type (e.g. Oak with Oak), opposite hinge, same facing, same half
        if (neighborDoor != clickedState.getBlock()) {
            return;
        }
        if (neighborState.getValue(DoorBlock.HINGE) == hinge) {
            return;
        }
        if (neighborState.getValue(DoorBlock.FACING) != facing) {
            return;
        }
        if (neighborState.getValue(DoorBlock.HALF) != half) {
            return;
        }
        if (neighborState.getValue(DoorBlock.OPEN) == isOpen) {
            return; // Already in matching state
        }

        smartdoubledoors$syncing.set(Boolean.TRUE);
        try {
            // Toggle the clicked half of neighbor
            level.setBlock(neighborPos, neighborState.setValue(DoorBlock.OPEN, isOpen), 10);

            // Toggle the other half (upper/lower) of neighbor as well to prevent door desync
            BlockPos otherHalfPos = (half == DoubleBlockHalf.LOWER) ? neighborPos.above() : neighborPos.below();
            BlockState otherHalfState = level.getBlockState(otherHalfPos);
            if (otherHalfState.is(neighborDoor) && otherHalfState.getValue(DoorBlock.OPEN) != isOpen) {
                level.setBlock(otherHalfPos, otherHalfState.setValue(DoorBlock.OPEN, isOpen), 10);
            }

            // Trigger vanilla game event (Block open / close) for listeners
            level.gameEvent(player, isOpen ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, neighborPos);
        } finally {
            smartdoubledoors$syncing.set(Boolean.FALSE);
        }
    }
}
