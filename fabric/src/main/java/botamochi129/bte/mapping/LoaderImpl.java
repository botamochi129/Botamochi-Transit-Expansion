package botamochi129.bte.mapping;

import botamochi129.bte.mod.data.AngleHelper;
import botamochi129.bte.mixin.mtr.InitAccessor;
import botamochi129.bte.mixin.mtr.MainAccessor;
import net.fabricmc.loader.api.FabricLoader;
import org.mtr.core.Main;
import org.mtr.core.data.Data;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.mapping.holder.*;
import org.mtr.mod.Init;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Fabric implementation via Yarn mapping
 */
public class LoaderImpl {
    public static boolean isRainingAt(World world, BlockPos pos) {
        return world.data.hasRain(pos.data);
    }

    /**
     * Creates a dynamic Angle instance from degrees via AngleHelper bridge.
     */
    public static Angle createDynamicAngle(String name, int ordinal, float degrees) {
        return AngleHelper.createDynamicAngle(name, ordinal, degrees);
    }

    /**
     * Returns the Data (Simulator) for the given world, or null if unavailable.
     */
    public static Data getDataForWorld(World world) {
        try {
            Object mainObj = InitAccessor.getMain();
            if (mainObj == null) return null;
            Main main = (Main) mainObj;

            String worldId = Init.getWorldId(world);
            var worldIdList = InitAccessor.getWorldIdList();
            int worldIndex = worldIdList.indexOf(worldId);
            if (worldIndex < 0) return null;

            var simulators = ((MainAccessor) (Object) main).getSimulators();
            if (worldIndex >= simulators.size()) return null;

            return (Data) simulators.get(worldIndex);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Converts a World holder to a ServerWorld holder, or null if not server-side.
     */
    public static ServerWorld toServerWorld(World world) {
        if (world == null) return null;
        if (world.data instanceof net.minecraft.server.world.ServerWorld sw) {
            return new ServerWorld(sw);
        }
        return null;
    }

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static BlockSettings createDefaultBlockSettings() {
        return new BlockSettings(net.minecraft.block.AbstractBlock.Settings.create());
    }

    /** Get a block settings forcing it to be solid, as we don't want water to break our block. */
    public static BlockSettings getSolidBlockSettings(BlockSettings settings) {
        #if MC_VERSION >= "12001"
            return new BlockSettings(settings.data.solid());
        #else
        return settings;
        #endif
    }

    public static Item getItemFromId(Identifier id) {
        final Optional<net.minecraft.item.Item> itm;
        #if MC_VERSION < "11903"
            itm = net.minecraft.util.registry.Registry.ITEM.getOrEmpty(id.data);
        #else
            itm = net.minecraft.registry.Registries.ITEM.getOrEmpty(id.data);
        #endif
        return itm.map(Item::new).orElse(null);
    }

    public static Identifier getIdFromItem(Item itm) {
        #if MC_VERSION < "11903"
            return new Identifier(net.minecraft.util.registry.Registry.ITEM.getId(itm.data));
        #else
            return new Identifier(net.minecraft.registry.Registries.ITEM.getId(itm.data));
        #endif
    }
}
