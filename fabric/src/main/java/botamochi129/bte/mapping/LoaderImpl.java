package botamochi129.bte.mapping;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.core.data.Data;
import org.mtr.mapping.holder.*;
import org.mtr.mod.Init;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Fabric implementation via Yarn mapping
 */
public class LoaderImpl {
    public static boolean isRainingAt(World world, BlockPos pos) {
        return world.data.hasRain(pos.data);
    }

    private static Field mainField;
    private static Field simulatorsField;
    private static Field worldIdListField;
    private static boolean reflectionFailed = false;

    static {
        try {
            mainField = Init.class.getDeclaredField("main");
            mainField.setAccessible(true);

            Object mainInstance = mainField.get(null);
            if (mainInstance != null) {
                simulatorsField = mainInstance.getClass().getDeclaredField("simulators");
                simulatorsField.setAccessible(true);
            }

            worldIdListField = Init.class.getDeclaredField("WORLD_ID_LIST");
            worldIdListField.setAccessible(true);
        } catch (Exception e) {
            reflectionFailed = true;
        }
    }

    /**
     * Returns the Data (Simulator) for the given world via reflection.
     * Accesses Init.main -> Main.simulators -> finds by WORLD_ID_LIST index.
     */
    @SuppressWarnings("unchecked")
    public static Data getDataForWorld(World world) {
        if (reflectionFailed) return null;
        try {
            Object mainInstance = mainField.get(null);
            if (mainInstance == null) return null;

            if (simulatorsField == null) {
                simulatorsField = mainInstance.getClass().getDeclaredField("simulators");
                simulatorsField.setAccessible(true);
            }
            Object simulatorsObj = simulatorsField.get(mainInstance);
            if (simulatorsObj == null) return null;

            Object worldIdListObj = worldIdListField.get(null);
            if (worldIdListObj == null) return null;

            String worldId = Init.getWorldId(world);
            if (worldId == null) return null;

            int index = ((List<?>) worldIdListObj).indexOf(worldId);
            if (index < 0) return null;

            Object simulator = ((List<?>) simulatorsObj).get(index);
            return (Data) simulator;
        } catch (Exception e) {
            reflectionFailed = true;
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
