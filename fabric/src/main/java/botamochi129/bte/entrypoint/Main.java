package botamochi129.bte.entrypoint;

import botamochi129.bte.mod.BTE;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.mtr.mapping.holder.World;

public class Main implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        BTE.initialize();

        // MTR の registerBlockEntityType は ticker を受け付けないため
        // StraightNodeBlockEntity#tick() はゲームから呼ばれない。
        // ワールド読み込みや MTR データ再読込のたびに BTE ノードを
        // 経路探索へ再反映させる必要があるため、ここで駆動する。
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            World world = primaryServerWorld(server);
            if (world != null) StraightNodeBlockEntity.serverTick(world);
        });

        // シングルプレイではタイトルへ戻るだけでサーバが作り直されるが JVM は同じなので、
        // 前セッションの弱参照が静的台帳に残る。再参加時の「角度を触るまで直らない」症状の
        // 主因なので、生成/破棄の両方で台帳を捨てる。
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StraightNodeBlockEntity.resetTracking();
            StraightNodeBlockEntity.beginDiscovery();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> StraightNodeBlockEntity.resetTracking());
    }

    /** BTE ノードが置かれることの多い Overworld を優先して返す。 */
    private static World primaryServerWorld(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(net.minecraft.world.World.OVERWORLD);
        if (overworld != null) return new World(overworld);
        for (ServerWorld sw : server.getWorlds()) {
            return new World(sw);
        }
        return null;
    }
}
