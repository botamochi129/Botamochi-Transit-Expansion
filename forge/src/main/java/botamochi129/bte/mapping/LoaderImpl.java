package botamochi129.bte.mapping;

import net.minecraftforge.fml.loading.FMLPaths;
import org.mtr.core.data.Data;
import org.mtr.core.tool.Angle;
import org.mtr.mapping.holder.*;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Forge implementation via Mojang mapping
 */
public class LoaderImpl {
    public static boolean isRainingAt(World world, BlockPos pos) {
        return world.data.isRainingAt(pos.data);
    }

    /**
     * Creates a dynamic Angle instance from degrees.
     * TODO: Forge equivalent of AngleMixin — for now returns closest existing enum value.
     */
    public static Angle createDynamicAngle(String name, int ordinal, float degrees) {
        Angle closest = Angle.values()[0];
        float minDiff = Float.MAX_VALUE;
        for (Angle a : Angle.values()) {
            float diff = Math.abs(a.angleDegrees - degrees);
            if (diff < minDiff) {
                minDiff = diff;
                closest = a;
            }
        }
        return closest;
    }

    /**
     * Returns the Data (Simulator) for the given world, or null if unavailable.
     * TODO: Forge equivalent of InitAccessor/MainAccessor.
     */
    public static Data getDataForWorld(World world) {
        return null;
    }

    /**
     * Converts a World holder to a ServerWorld holder, or null if not server-side.
     */
    public static ServerWorld toServerWorld(World world) {
        if (world == null) return null;
        if (world.data instanceof net.minecraft.server.level.ServerLevel sl) {
            return new ServerWorld(sl);
        }
        return null;
    }

    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static BlockSettings createDefaultBlockSettings() {
        return new BlockSettings(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of());
    }

    /** Get a block settings forcing it to be solid, as we don't want water to break our block. */
    public static BlockSettings getSolidBlockSettings(BlockSettings settings) {
        #if MC_VERSION >= "12001"
            return new BlockSettings(settings.data.forceSolidOn());
        #else
            return settings;
        #endif
    }

    public static Item getItemFromId(Identifier id) {
        #if MC_VERSION < "11903"
            final Optional<net.minecraft.world.item.Item> itm;
            itm = net.minecraft.core.Registry.ITEM.getOptional(id.data);
        #else
            final Optional<net.minecraft.world.item.Item> itm;
            itm = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id.data);
        #endif
        return itm.map(Item::new).orElse(null);
    }

    public static Identifier getIdFromItem(Item itm) {
        #if MC_VERSION < "11903"
            return new Identifier(net.minecraft.core.Registry.ITEM.getKey(itm.data));
        #else
            return new Identifier(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itm.data));
        #endif
    }
}
