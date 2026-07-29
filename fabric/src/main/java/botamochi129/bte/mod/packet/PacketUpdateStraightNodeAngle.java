package botamochi129.bte.mod.packet;

import botamochi129.bte.mod.block.StraightNodeBlock;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;

public class PacketUpdateStraightNodeAngle extends PacketHandler {
    private static final double UNBOUND_SENTINEL = -129129.0;

    private final BlockPos blockPos;
    private final double angle;

    public PacketUpdateStraightNodeAngle(PacketBufferReceiver receiver) {
        this.blockPos = BlockPos.fromLong(receiver.readLong());
        this.angle = receiver.readDouble();
    }

    public PacketUpdateStraightNodeAngle(BlockPos blockPos, double angle) {
        this.blockPos = blockPos;
        this.angle = angle;
    }

    @Override
    public void write(PacketBufferSender packetBufferSender) {
        packetBufferSender.writeLong(blockPos.asLong());
        packetBufferSender.writeDouble(angle);
    }

    @Override
    public void runServer(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity) {
        if (minecraftServer == null) return;

        // ★重要: パケット受信時の処理をサーバーのメインスレッドにスケジュールしてConcurrentModificationExceptionを防止
        minecraftServer.execute(() -> {
            World world = serverPlayerEntity.getEntityWorld();

            // 1. チャンクが読み込まれているかチェック
            if (!Init.isChunkLoaded(world, blockPos)) {
                return;
            }

            // 2. ブロックが StraightNodeBlock かチェック
            BlockState state = world.getBlockState(blockPos);
            if (!(state.getBlock().data instanceof StraightNodeBlock)) {
                return;
            }

            // 3. BlockEntity の取得
            org.mtr.mapping.holder.BlockEntity rawBe = world.getBlockEntity(blockPos);
            if (rawBe == null || !(rawBe.data instanceof StraightNodeBlockEntity be)) {
                return;
            }

            // 4. センチネル値判定（解除か角度設定か）
            if (angle <= UNBOUND_SENTINEL + 1.0D) {
                if (be.isBound()) { // すでにUnboundなら無駄な処理をスキップ
                    be.unbind();
                }
            } else {
                // 角度が変わっていない場合は無駄なレール計算(updateRailwayData)をスキップして負荷を抑える
                if (!be.isBound() || Math.abs(be.getAngleDegrees() - angle) > 0.001) {
                    be.bind(angle);
                }
            }
        });
    }
}