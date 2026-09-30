package botamochi129.bte.mod.packet;

import botamochi129.bte.mod.block.StraightNodeBlock;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;

/**
 * 「変更前の MTR 既定制限速度」を BTE ノードのブロックエンティティへ記録するパケット。
 * <p>
 * 実際の制限速度の変更は MTR 標準の {@code UpdateDataRequest} が担うため、本パケットは
 * 速度そのものを扱わない。役割は「既定値へ戻す」ための退避データだけを確実に保存することだけである。
 * <p>
 * 退避値を<b>クライアント側で変更前に採取</b>して送ることで、
 * サーバーでの適用順序に依存せず正しい既定値を保持できる。
 * (MTR の速度値は {@code RailSchema} がワールドセーブへ永続化しているため、
 * BTE 側は退避データだけを NBT に残せばよい)
 */
public class PacketRememberRailSpeedLimit extends PacketHandler {

    private final BlockPos blockPos;
    private final String railHexId;
    private final long originalSpeedLimit1Kmh;
    private final long originalSpeedLimit2Kmh;
    private final boolean forget;

    public PacketRememberRailSpeedLimit(PacketBufferReceiver receiver) {
        this.blockPos = BlockPos.fromLong(receiver.readLong());
        this.railHexId = receiver.readString();
        this.originalSpeedLimit1Kmh = receiver.readLong();
        this.originalSpeedLimit2Kmh = receiver.readLong();
        this.forget = receiver.readBoolean();
    }

    public PacketRememberRailSpeedLimit(BlockPos blockPos, String railHexId,
                                        long originalSpeedLimit1Kmh, long originalSpeedLimit2Kmh,
                                        boolean forget) {
        this.blockPos = blockPos;
        this.railHexId = railHexId;
        this.originalSpeedLimit1Kmh = originalSpeedLimit1Kmh;
        this.originalSpeedLimit2Kmh = originalSpeedLimit2Kmh;
        this.forget = forget;
    }

    @Override
    public void write(PacketBufferSender packetBufferSender) {
        packetBufferSender.writeLong(blockPos.asLong());
        packetBufferSender.writeString(railHexId);
        packetBufferSender.writeLong(originalSpeedLimit1Kmh);
        packetBufferSender.writeLong(originalSpeedLimit2Kmh);
        packetBufferSender.writeBoolean(forget);
    }

    @Override
    public void runServer(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity) {
        if (minecraftServer == null || railHexId == null || railHexId.isEmpty()) return;

        minecraftServer.execute(() -> {
            final World world = serverPlayerEntity.getEntityWorld();
            if (!Init.isChunkLoaded(world, blockPos)) return;

            final BlockState state = world.getBlockState(blockPos);
            if (!(state.getBlock().data instanceof StraightNodeBlock)) return;

            final BlockEntity rawBe = world.getBlockEntity(blockPos);
            if (rawBe == null || !(rawBe.data instanceof StraightNodeBlockEntity be)) return;

            final long[] existing = be.getOriginalSpeedLimit(railHexId);
            if (forget) {
                // 「既定値へ戻した」完了通知。記録を破棄して次回の変更時に新しく採取させる。
                be.forgetOriginalSpeedLimit(railHexId);
            } else if (existing == null) {
                // 既に記録済みの場合は「2 回目以降の変更」なので元の既定値を上書きしない。
                be.rememberOriginalSpeedLimit(railHexId, originalSpeedLimit1Kmh, originalSpeedLimit2Kmh);
            }
            be.markDirty2();
        });
    }
}
