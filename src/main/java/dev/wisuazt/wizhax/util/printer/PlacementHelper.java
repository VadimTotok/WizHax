package dev.wisuazt.wizhax.util.printer;

import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

public final class PlacementHelper {
    private PlacementHelper() {}

    public static boolean isRotationDependent(BlockState state) {
        if (state == null) return false;
        return state.contains(Properties.HORIZONTAL_FACING)
            || state.contains(Properties.FACING)
            || state.contains(Properties.HOPPER_FACING)
            || state.contains(Properties.AXIS)
            || state.contains(Properties.ROTATION)
            || state.contains(Properties.SLAB_TYPE)
            || state.contains(Properties.BLOCK_HALF);
    }

    public static Direction getPlacementSide(BlockState state) {
        if (state.contains(Properties.SLAB_TYPE)) {
            SlabType type = state.get(Properties.SLAB_TYPE);
            return type == SlabType.TOP ? Direction.DOWN : Direction.UP;
        }
        if (state.contains(Properties.BLOCK_HALF)) {
            BlockHalf half = state.get(Properties.BLOCK_HALF);
            return half == BlockHalf.TOP ? Direction.DOWN : Direction.UP;
        }
        if (state.contains(Properties.HOPPER_FACING)) {
            return state.get(Properties.HOPPER_FACING).getOpposite();
        }
        if (state.contains(Properties.FACING)) {
            return state.get(Properties.FACING).getOpposite();
        }
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            return state.get(Properties.HORIZONTAL_FACING).getOpposite();
        }
        return Direction.DOWN;
    }

    public static float[] getRotations(BlockState requiredState) {
        float yaw = 0f;
        float pitch = 0f;
        Block block = requiredState.getBlock();

        if (requiredState.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = requiredState.get(Properties.HORIZONTAL_FACING);
            boolean sameDir = block instanceof StairsBlock
                || block instanceof DoorBlock
                || block instanceof FenceGateBlock;
            yaw = yawOf(sameDir ? facing : facing.getOpposite());
        }

        if (requiredState.contains(Properties.FACING)) {
            Direction facing = requiredState.get(Properties.FACING);
            if (facing.getAxis().isVertical()) {
                pitch = (facing == Direction.UP) ? 90f : -90f;
            } else {
                boolean sameDir = block instanceof ObserverBlock;
                yaw = yawOf(sameDir ? facing : facing.getOpposite());
            }
        }

        if (requiredState.contains(Properties.ROTATION)) {
            int rot = requiredState.get(Properties.ROTATION);
            yaw = rot * 22.5f;
        }

        return new float[]{yaw, pitch};
    }

    private static float yawOf(Direction dir) {
        if (dir.getAxis().isVertical()) return 0f;
        return Direction.getHorizontalDegreesOrThrow(dir);
    }
}
