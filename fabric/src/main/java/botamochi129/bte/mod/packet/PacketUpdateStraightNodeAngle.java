package botamochi129.bte.mod.packet;

import botamochi129.bte.mapping.LoaderImpl;
import botamochi129.bte.mod.block.StraightNodeBlock;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;

public class PacketUpdateStraightNodeAngle extends PacketHandler {
    private static final double UNBOUND_SENTINEL = -114514.0;

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
        ServerWorld world = serverPlayerEntity.getServerWorld();
        World worldHolder = new World(world.data);
        if (!Init.isChunkLoaded(worldHolder, blockPos)) {
            return;
        }
        BlockState state = world.getBlockState(blockPos);
        if (!(state.getBlock().data instanceof StraightNodeBlock)) {
            return;
        }

        org.mtr.mapping.holder.BlockEntity rawBe = world.getBlockEntity(blockPos);
        StraightNodeBlockEntity be = null;
        if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity snbe) {
            be = snbe;
        }

        if (angle <= UNBOUND_SENTINEL + 1) {
            if (be != null) {
                be.unbind();
            }
        } else {
            int intAngle = (int) Math.round(angle);
            world.setBlockState(blockPos, StraightNodeBlock.setAngle(state, intAngle));
            if (be != null) {
                be.setAngleDegrees(angle);
                be.updateConnectedRails();
            }
        }
    }
}
