package dev.wisuazt.wizhax.compat;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class LitematicaHelper {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("litematica");

    private LitematicaHelper() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    @Nullable
    public static WorldSchematic getSchematicWorld() {
        if (!LOADED) return null;
        try {
            return SchematicWorldHandler.getSchematicWorld();
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    public static BlockState getSchematicBlockState(BlockPos pos) {
        WorldSchematic world = getSchematicWorld();
        if (world == null) return null;
        try {
            return world.getBlockState(pos);
        } catch (Throwable t) {
            return null;
        }
    }
}
