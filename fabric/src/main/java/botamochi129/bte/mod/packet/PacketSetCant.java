package botamochi129.bte.mod.packet;

import botamochi129.bte.mapping.LoaderImpl;
import botamochi129.bte.mod.block.StraightNodeBlock;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.RailAccessor;
import botamochi129.bte.mod.rail.CantProfile;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TwoPositionsBase;
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
 * レール 1 本のカント設定を BTE ノードへ書き込むパケット。
 *
 * <p>保存は常にレールの「正規方向」（{@code Rail#getHexId()} が決める端点順）で行う。
 * クライアントは自身が見ている向きの値をそのまま送り、{@code isCanonicalStart} で
 * その向きが正規方向と一致するかを伝える。サーバー側で必要なら反転して保存する。
 *
 * <p>対向端が BTE ノードであれば同じ値も書き込む。こうすることで、
 * どちらのノードから編集しても BTE 側の 2 つの BE が食い違わない。
 *
 * <p>また、クライアント側の rail.getHexId() とサーバー側の正規 hexId が異なる場合
 * （レール再作成時に端点順序が変わった等）に備え、正規 hexId でも保存する。
 */
public class PacketSetCant extends PacketHandler {

    private final BlockPos blockPos;
    private final String railHexId;
    private final float startDeg;
    private final float middleDeg;
    private final float endDeg;
    private final boolean isCanonicalStart;

    public PacketSetCant(PacketBufferReceiver receiver) {
        this.blockPos = BlockPos.fromLong(receiver.readLong());
        this.railHexId = receiver.readString();
        this.startDeg = receiver.readFloat();
        this.middleDeg = receiver.readFloat();
        this.endDeg = receiver.readFloat();
        this.isCanonicalStart = receiver.readBoolean();
    }

    public PacketSetCant(BlockPos blockPos, String railHexId,
                         float startDeg, float middleDeg, float endDeg,
                         boolean isCanonicalStart) {
        this.blockPos = blockPos;
        this.railHexId = railHexId;
        this.startDeg = startDeg;
        this.middleDeg = middleDeg;
        this.endDeg = endDeg;
        this.isCanonicalStart = isCanonicalStart;
    }

    @Override
    public void write(PacketBufferSender packetBufferSender) {
        packetBufferSender.writeLong(blockPos.asLong());
        packetBufferSender.writeString(railHexId);
        packetBufferSender.writeFloat(startDeg);
        packetBufferSender.writeFloat(middleDeg);
        packetBufferSender.writeFloat(endDeg);
        packetBufferSender.writeBoolean(isCanonicalStart);
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

            // ユーザーが見ている向きの値を、常に正規方向の値へ直して保存する
            CantProfile profile = new CantProfile(startDeg, middleDeg, endDeg);
            if (!isCanonicalStart) {
                profile = profile.reversed();
            }

            // パケットの railHexId と正規 hexId の両方で保存（hexId 不一致対策）
            be.setCant(railHexId, profile);
            be.markDirty2();
            be.syncToClients();

            // 正規 hexId も計算して保存
            final Data data = LoaderImpl.getDataForWorld(world);
            if (data != null) {
                final Rail rail = data.railIdMap.get(railHexId);
                if (rail != null && ((Object) rail) instanceof RailAccessor accessor) {
                    final Position p1 = accessor.bte$getPosition1();
                    final Position p2 = accessor.bte$getPosition2();
                    if (p1 != null && p2 != null) {
                        final String canonicalHexId = TwoPositionsBase.getHexId(p1, p2);
                        if (canonicalHexId != null && !canonicalHexId.equals(railHexId)) {
                            be.setCant(canonicalHexId, profile);
                            be.markDirty2();
                            be.syncToClients();
                        }
                    }
                }
            }

            applyToOtherEndpoint(world, railHexId, profile);
        });
    }

    /**
     * 対向端が BTE ノードなら同じ値を書き込み、2 つの BE を一致させる。
     * MTR の Data からレール端点を引くため、BTE ノードでない端は自然に無視される。
     */
    private void applyToOtherEndpoint(World world, String railHexId, CantProfile profile) {
        final Data data = LoaderImpl.getDataForWorld(world);
        if (data == null) return;

        final Rail rail = data.railIdMap.get(railHexId);
        if (rail == null) return;
        if (!(((Object) rail) instanceof RailAccessor accessor)) return;

        final long selfLong = blockPos.asLong();
        final Position position1 = accessor.bte$getPosition1();
        final Position position2 = accessor.bte$getPosition2();

        BlockPos other = null;
        if (position1 != null && Init.positionToBlockPos(position1).asLong() != selfLong) {
            other = Init.positionToBlockPos(position1);
        } else if (position2 != null && Init.positionToBlockPos(position2).asLong() != selfLong) {
            other = Init.positionToBlockPos(position2);
        }
        if (other == null) return;

        final BlockEntity otherRaw = world.getBlockEntity(other);
        if (otherRaw == null || !(otherRaw.data instanceof StraightNodeBlockEntity otherBe)) return;

        otherBe.setCant(railHexId, profile);
        otherBe.markDirty2();
        otherBe.syncToClients();

        // 対向端にも正規 hexId で保存
        if (((Object) rail) instanceof RailAccessor accessor2) {
            final Position p1 = accessor2.bte$getPosition1();
            final Position p2 = accessor2.bte$getPosition2();
            if (p1 != null && p2 != null) {
                final String canonicalHexId = TwoPositionsBase.getHexId(p1, p2);
                if (canonicalHexId != null && !canonicalHexId.equals(railHexId)) {
                    otherBe.setCant(canonicalHexId, profile);
                    otherBe.markDirty2();
                    otherBe.syncToClients();
                }
            }
        }
    }
}
