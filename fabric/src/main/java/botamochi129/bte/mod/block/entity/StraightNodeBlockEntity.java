package botamochi129.bte.mod.block.entity;

import botamochi129.bte.mapping.LoaderImpl;
import botamochi129.bte.mod.block.StraightNodeBlock;
import botamochi129.bte.mod.data.AngleExtra;
import botamochi129.bte.mod.registry.Blocks;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.block.IBlock;
import org.mtr.mod.packet.PacketUpdateData;

import java.util.Map;

public class StraightNodeBlockEntity extends BlockEntityExtension {

    private static final String KEY_ANGLE = "angle_degrees";
    private static final double UNBOUND_SENTINEL = -114514.0;
    private double angleDegrees = UNBOUND_SENTINEL;

    public StraightNodeBlockEntity(BlockPos pos, BlockState state) {
        super(Blocks.STRAIGHT_NODE_BE.get(), pos, state);
    }

    public StraightNodeBlockEntity(BlockPos pos, BlockState state, double angle) {
        super(Blocks.STRAIGHT_NODE_BE.get(), pos, state);
        this.angleDegrees = angle;
    }

    public double getAngleDegrees() {
        return angleDegrees;
    }

    public void setAngleDegrees(double angleDegrees) {
        this.angleDegrees = normalize(angleDegrees);
        markDirty2();
        syncBlockState();
    }

    public Angle getAngle() {
        if (!isBound()) return null;
        return AngleExtra.fromDegrees(angleDegrees);
    }

    public boolean isBound() {
        return angleDegrees != UNBOUND_SENTINEL;
    }

    public void bind(StraightNodeBlockEntity other) {
        if (isBound()) return;
        BlockPos thi = getPos2();
        BlockPos oth = other.getPos2();
        bind(Math.toDegrees(Math.atan2(oth.getZ() - thi.getZ(), oth.getX() - thi.getX())));
        other.bind(this);
    }

    public void bind(double angle) {
        angle = normalize(angle);
        this.angleDegrees = angle;
        markDirty2();
        syncBlockState();
    }

    public void unbind() {
        if (!isBound()) return;
        angleDegrees = UNBOUND_SENTINEL;
        markDirty2();
        syncBlockState();
    }

    public boolean isConnected() {
        org.mtr.mapping.holder.World world = getWorld2();
        if (world == null) return false;
        BlockState state = world.getBlockState(getPos2());
        return IBlock.getStatePropertySafe(state, BlockNode.IS_CONNECTED);
    }

    private void syncBlockState() {
        org.mtr.mapping.holder.World world = getWorld2();
        if (world == null || world.isClient()) return;
        BlockPos pos = getPos2();
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock().data instanceof StraightNodeBlock)) return;
        int blockAngle = StraightNodeBlock.getAngle2(state);
        int targetAngle = isBound() ? (int) Math.round(angleDegrees) : 0;
        if (blockAngle != targetAngle) {
            world.setBlockState(pos, StraightNodeBlock.setAngle(state, targetAngle));
        }
    }

    public void updateConnectedRails() {
        if (!isBound()) return;
        org.mtr.mapping.holder.World world = getWorld2();
        if (world == null || world.isClient()) return;

        Data data = getDataForWorld(world);
        if (data == null) return;

        Position nodePos = Init.blockPosToPosition(getPos2());
        Angle newAngle = getAngle();
        if (newAngle == null) return;

        Map<Position, Rail> railsAtPos = data.positionsToRail.get(nodePos);
        if (railsAtPos == null) return;

        for (Map.Entry<Position, Rail> entry : railsAtPos.entrySet()) {
            Position otherPos = entry.getKey();
            Rail oldRail = entry.getValue();
            Rail newRail = createUpdatedRail(oldRail, nodePos, otherPos, newAngle);
            if (newRail != null) {
                ServerWorld sw = LoaderImpl.toServerWorld(world);
                if (sw != null) {
                    PacketUpdateData.sendDirectlyToServerRail(sw, newRail);
                }
            }
        }
    }

    private Rail createUpdatedRail(Rail oldRail, Position nodePos, Position otherPos, Angle newAngle) {
        org.mtr.mapping.holder.World world = getWorld2();
        if (world == null) return null;

        BlockPos otherBlockPos = Init.positionToBlockPos(otherPos);
        BlockState otherState = world.getBlockState(otherBlockPos);
        Angle otherAngle;
        if (otherState.getBlock().data instanceof StraightNodeBlock) {
            double otherDeg = StraightNodeBlock.getAngle2(otherState);
            org.mtr.mapping.holder.BlockEntity rawBe = world.getBlockEntity(otherBlockPos);
            if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity snbe && snbe.isBound()) {
                otherDeg = snbe.getAngleDegrees();
            }
            otherAngle = AngleExtra.fromDegrees(otherDeg);
        } else {
            otherAngle = AngleExtra.fromDegrees(BlockNode.getAngle(otherState));
        }

        float geoAngleDeg = (float) Math.toDegrees(Math.atan2(
                otherPos.getZ() - nodePos.getZ(),
                otherPos.getX() - nodePos.getX()
        ));
        float reverseGeoAngleDeg = (float) Math.toDegrees(Math.atan2(
                nodePos.getZ() - otherPos.getZ(),
                nodePos.getX() - otherPos.getX()
        ));

        float nodeDeg = newAngle.angleDegrees;
        float correctedNodeDeg = Angle.similarFacing(nodeDeg, geoAngleDeg) ? nodeDeg : nodeDeg + 180;
        Angle correctedNodeAngle = AngleExtra.fromDegrees(correctedNodeDeg);

        float otherDegF = otherAngle.angleDegrees;
        float correctedOtherDeg = Angle.similarFacing(otherDegF, reverseGeoAngleDeg) ? otherDegF : otherDegF + 180;
        Angle correctedOtherAngle = AngleExtra.fromDegrees(correctedOtherDeg);

        double nodeSpeedMs = oldRail.getSpeedLimitMetersPerMillisecond(nodePos);
        double otherSpeedMs = oldRail.getSpeedLimitMetersPerMillisecond(otherPos);
        long nodeSpeedKmh = (long) Math.floor(nodeSpeedMs * 3600);
        long otherSpeedKmh = (long) Math.floor(otherSpeedMs * 3600);

        return Rail.newRail(
                nodePos, correctedNodeAngle, otherPos, correctedOtherAngle,
                oldRail.railMath.getShape(),
                oldRail.railMath.getVerticalRadius(),
                new ObjectArrayList<>(oldRail.getStyles()),
                nodeSpeedKmh, otherSpeedKmh,
                oldRail.isPlatform(),
                oldRail.isSiding(),
                oldRail.canAccelerate(),
                oldRail.canTurnBack(),
                oldRail.canConnectRemotely(),
                oldRail.getTransportMode()
        );
    }

    @Override
    public void readCompoundTag(CompoundTag tag) {
        if (tag.contains(KEY_ANGLE)) {
            angleDegrees = tag.getDouble(KEY_ANGLE);
        } else {
            angleDegrees = UNBOUND_SENTINEL;
        }
    }

    @Override
    public void writeCompoundTag(CompoundTag tag) {
        if (isBound()) {
            tag.putDouble(KEY_ANGLE, angleDegrees);
        }
    }

    public static double normalize(double angle) {
        while (angle < 0) angle += 180D;
        while (angle >= 180) angle -= 180D;
        return angle;
    }

    private static Data getDataForWorld(org.mtr.mapping.holder.World world) {
        return LoaderImpl.getDataForWorld(world);
    }
}
