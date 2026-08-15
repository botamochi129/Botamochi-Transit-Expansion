package botamochi129.bte.mapping;

import net.minecraftforge.fml.loading.FMLPaths;
import org.mtr.core.data.Data;
import org.mtr.mapping.holder.*;
import org.mtr.mod.Init;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Forge implementation via Mojang mapping
 */
public class LoaderImpl {

    // Forge (Mojang): Level.isRainingAt(BlockPos)
    public static boolean isRainingAt(World world, BlockPos pos) {
        return world.data.isRainingAt(pos.data);
    }

    // MTR Core の内部構造は Fabric/Forge 共通のため、リフレクション部分は同じで動作します
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

    public static ServerWorld toServerWorld(World world) {
        if (world == null) return null;
        // ★ Forge (Mojang): net.minecraft.server.world.ServerWorld ではなく ServerLevel
        if (world.data instanceof net.minecraft.server.level.ServerLevel sw) {
            return new ServerWorld(sw);
        }
        return null;
    }

    public static Path getConfigPath() {
        // ★ Forge: FMLPaths を使用
        return FMLPaths.CONFIGDIR.get();
    }

    public static BlockSettings createDefaultBlockSettings() {
        // ★ Forge (Mojang): AbstractBlock.Settings ではなく BlockBehaviour.Properties.of()
        return new BlockSettings(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of());
    }

    public static BlockSettings getSolidBlockSettings(BlockSettings settings) {
        // Forge側では、Yarnの solid() に相当する直接のメソッドが Properties にない場合が多いため、
        // デフォルトの挙動（solidとして扱う）に任せ、そのまま返すのが安全です。
        return settings;
    }

    public static Item getItemFromId(Identifier id) {
        final Optional<net.minecraft.world.item.Item> itm;
        #if MC_VERSION < "11903"
            itm = net.minecraft.core.Registry.ITEM.getOptional(id.data);
        #else
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

    public static void sendBlockEntityUpdatePacket(World world, BlockPos pos) {
        if (world.data instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            net.minecraft.world.level.block.entity.BlockEntity be = serverLevel.getBlockEntity(pos.data);
            if (be != null) {
                net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet =
                        net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(be);
                for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
                    player.connection.send(packet);
                }
            }
        }
    }
}