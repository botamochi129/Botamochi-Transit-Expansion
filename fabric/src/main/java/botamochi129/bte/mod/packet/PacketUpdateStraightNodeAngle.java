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
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double rollDegrees; // ★ 追加

    public PacketUpdateStraightNodeAngle(PacketBufferReceiver receiver) {
        this.blockPos = BlockPos.fromLong(receiver.readLong());
        this.angle = receiver.readDouble();
        this.offsetX = receiver.readDouble();
        this.offsetY = receiver.readDouble();
        this.offsetZ = receiver.readDouble();
        this.rollDegrees = receiver.readDouble(); // ★ 追加
    }

    public PacketUpdateStraightNodeAngle(BlockPos blockPos, double angle, double offsetX, double offsetY, double offsetZ, double rollDegrees) {
        this.blockPos = blockPos;
        this.angle = angle;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.rollDegrees = rollDegrees; // ★ 追加
    }

    @Override
    public void write(PacketBufferSender packetBufferSender) {
        packetBufferSender.writeLong(blockPos.asLong());
        packetBufferSender.writeDouble(angle);
        packetBufferSender.writeDouble(offsetX);
        packetBufferSender.writeDouble(offsetY);
        packetBufferSender.writeDouble(offsetZ);
        packetBufferSender.writeDouble(rollDegrees); // ★ 追加
    }

    @Override
    public void runServer(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity) {
        if (minecraftServer == null) return;

        minecraftServer.execute(() -> {
            World world = serverPlayerEntity.getEntityWorld();

            if (!Init.isChunkLoaded(world, blockPos)) return;

            BlockState state = world.getBlockState(blockPos);
            if (!(state.getBlock().data instanceof StraightNodeBlock)) return;

            org.mtr.mapping.holder.BlockEntity rawBe = world.getBlockEntity(blockPos);
            if (rawBe == null || !(rawBe.data instanceof StraightNodeBlockEntity be)) return;

            boolean needsUpdate = false;

            // 1. 角度の適用
            if (angle <= UNBOUND_SENTINEL + 1.0D) {
                if (be.isBound()) {
                    be.unbind();
                    needsUpdate = true;
                }
            } else {
                if (!be.isBound() || Math.abs(be.getAngleDegrees() - angle) > 0.001) {
                    be.bind(angle);
                    needsUpdate = true;
                }
            }

            // 2. オフセットの適用
            if (Math.abs(be.getOffsetX() - offsetX) > 0.001 ||
                    Math.abs(be.getOffsetY() - offsetY) > 0.001 ||
                    Math.abs(be.getOffsetZ() - offsetZ) > 0.001) {
                be.setOffset(offsetX, offsetY, offsetZ);
                needsUpdate = true;
            }

            // 3. カント（ロール）の適用 ★ 追加
            if (Math.abs(be.getRollDegrees() - rollDegrees) > 0.001) {
                be.setRollDegrees(rollDegrees);
                needsUpdate = true;
            }

            // 4. 変更があればレールデータを更新
            if (needsUpdate) {
                be.updateConnectedRails(true);
                be.markDirty2();
            }
        });
    }
}