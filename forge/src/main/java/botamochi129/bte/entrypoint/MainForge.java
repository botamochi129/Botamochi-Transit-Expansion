package botamochi129.bte.entrypoint;

import botamochi129.bte.mod.Constants;
import botamochi129.bte.mod.BTE;
import botamochi129.bte.mod.BTEClient;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.mtr.mapping.holder.World;

@Mod(Constants.MOD_ID)
public class MainForge {
    public MainForge() {
        BTE.initialize();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ForgeConfig.registerConfig();
            BTEClient.initialize();
        });
        MinecraftForge.EVENT_BUS.register(new MigrateMapping());
        MinecraftForge.EVENT_BUS.register(new ServerLifecycle());
    }

    /**
     * Fabric の {@code entrypoint/Main.java} に対応するサーバ側ライフサイクル登録。
     *
     * <p>これが無いと BTE の自己修復機構が Forge 側で<strong>一切動かない</strong>:
     * <ul>
     *   <li>{@code serverTick} が回らない ⇒ {@code StraightNodeBlockEntity#drain()} が走らず、
     *       {@code updateBezierDataOnly} による 4 tick 周期の publish が行われない
     *       （角度編集の反映が {@code bind} / {@code setOffset} / {@code unbind} の瞬間だけになる）</li>
     *   <li>{@code resetTracking()} が呼ばれない ⇒ {@code CantRegistry.clear()} が永久に実行されず、
     *       キー {@code Rail#getHexId()}（端点座標 + 形状のみ）で引いたカントが
     *       セッションを跨いで残留する。同じ位置に建て直した MTR 標準レールが同じ hexId を継承し、
     *       意図しないカントでレール模型が傾く</li>
     * </ul>
     */
    public static class ServerLifecycle {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            final MinecraftServer server = event.getServer();
            if (server == null) return;
            final World world = primaryServerWorld(server);
            if (world != null) StraightNodeBlockEntity.serverTick(world);
        }

        @SubscribeEvent
        public void onServerStarted(ServerStartedEvent event) {
            StraightNodeBlockEntity.resetTracking();
            StraightNodeBlockEntity.beginDiscovery();
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            StraightNodeBlockEntity.resetTracking();
        }
    }

    /** BTE ノードが置かれることの多い Overworld を優先して返す。 */
    private static World primaryServerWorld(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld != null) return new World(overworld);
        for (ServerLevel sl : server.getAllLevels()) {
            return new World(sl);
        }
        return null;
    }
}