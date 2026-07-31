package botamochi129.bte.mod.block.entity;

import botamochi129.bte.mapping.LoaderImpl;
import botamochi129.bte.mod.data.AngleExtra;
import botamochi129.bte.mod.data.IRailMathExtra;
import botamochi129.bte.mod.registry.Blocks;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.block.IBlock;
import org.mtr.mod.packet.PacketUpdateData;

import java.util.Map;

public class StraightNodeBlockEntity extends BlockEntityExtension {

    private static final String KEY_ANGLE = "angle_degrees";
    public static final double UNBOUND_SENTINEL = -129129.0;

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
        this.angleDegrees = normalize(angle);
        markDirty2();
        syncBlockEntity();
        updateConnectedRails();
    }

    public void unbind() {
        if (!isBound()) return;
        angleDegrees = UNBOUND_SENTINEL;
        markDirty2();
        syncBlockEntity();
    }

    public boolean isConnected() {
        World world = getWorld2();
        if (world == null) return false;
        BlockState state = world.getBlockState(getPos2());
        return IBlock.getStatePropertySafe(state, BlockNode.IS_CONNECTED);
    }

    private void syncBlockEntity() {
        World world = getWorld2();
        if (world != null && !world.isClient()) {
            ServerWorld sw = LoaderImpl.toServerWorld(world);
            if (sw != null) {
                sw.getChunkManager().markForUpdate(getPos2());
            }
        }
    }

    public void updateConnectedRails() {
        if (!isBound()) return;
        World world = getWorld2();
        if (world == null || world.isClient()) return;

        Data data = LoaderImpl.getDataForWorld(world);
        if (data == null) return;

        Position nodePos = Init.blockPosToPosition(getPos2());
        double selfDeg = this.angleDegrees;

        Map<Position, Rail> railsAtPos = data.positionsToRail.get(nodePos);
        if (railsAtPos == null) return;

        ServerWorld serverWorld = LoaderImpl.toServerWorld(world);

        for (Map.Entry<Position, Rail> entry : railsAtPos.entrySet()) {
            Position otherPos = entry.getKey();
            Rail oldRail = entry.getValue();

            Rail newRail = createUpdatedRail(oldRail, nodePos, otherPos, selfDeg);

            if (newRail != null && newRail.railMath != null && newRail.railMath instanceof IRailMathExtra mathExtra) {

                // 【重要修正】幾何学的な方向を計算
                float geoAngleDeg = (float) Math.toDegrees(Math.atan2(
                        otherPos.getZ() - nodePos.getZ(),
                        otherPos.getX() - nodePos.getX()
                ));
                float reverseGeoAngleDeg = (float) Math.toDegrees(Math.atan2(
                        nodePos.getZ() - otherPos.getZ(),
                        nodePos.getX() - otherPos.getX()
                ));

                // 相手の角度を取得
                BlockPos otherBlockPos = Init.positionToBlockPos(otherPos);
                double otherDeg = BlockNode.getAngle(world.getBlockState(otherBlockPos));
                BlockEntity rawBe = world.getBlockEntity(otherBlockPos);
                if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity snbe && snbe.isBound()) {
                    otherDeg = snbe.getAngleDegrees();
                }

                // 【核心修正】similarFacing で補正した角度をベジェ曲線に渡す
                // これにより、MTR標準ノードが逆向きでも、描画の接線が正しく揃う
                double correctedSelfDeg = Angle.similarFacing((float) selfDeg, geoAngleDeg) ? selfDeg : selfDeg + 180;
                double correctedOtherDeg = Angle.similarFacing((float) otherDeg, reverseGeoAngleDeg) ? otherDeg : otherDeg + 180;

                double startRad = Math.toRadians(correctedSelfDeg);
                double endRad = Math.toRadians(correctedOtherDeg);

                Vector startVec = new Vector(nodePos.getX() + 0.5, nodePos.getY(), nodePos.getZ() + 0.5);
                Vector endVec = new Vector(otherPos.getX() + 0.5, otherPos.getY(), otherPos.getZ() + 0.5);

                double verticalRadius = oldRail.railMath.getVerticalRadius();
                mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad, verticalRadius);

                data.rails.remove(oldRail);
                data.rails.add(newRail);
                data.positionsToRail.get(nodePos).put(otherPos, newRail);
                data.positionsToRail.get(otherPos).put(nodePos, newRail);
            }

            if (serverWorld != null && newRail != null) {
                PacketUpdateData.sendDirectlyToServerRail(serverWorld, newRail);
            }
        }
    }

    private Rail createUpdatedRail(Rail oldRail, Position nodePos, Position otherPos, double selfDeg) {
        World world = getWorld2();
        if (world == null) return null;

        BlockPos otherBlockPos = Init.positionToBlockPos(otherPos);
        BlockState otherState = world.getBlockState(otherBlockPos);

        double otherDeg = BlockNode.getAngle(otherState);
        BlockEntity rawBe = world.getBlockEntity(otherBlockPos);
        if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity snbe && snbe.isBound()) {
            otherDeg = snbe.getAngleDegrees();
        }

        float geoAngleDeg = (float) Math.toDegrees(Math.atan2(
                otherPos.getZ() - nodePos.getZ(),
                otherPos.getX() - nodePos.getX()
        ));
        float reverseGeoAngleDeg = (float) Math.toDegrees(Math.atan2(
                nodePos.getZ() - otherPos.getZ(),
                nodePos.getX() - otherPos.getX()
        ));

        float correctedNodeDeg = Angle.similarFacing((float) selfDeg, geoAngleDeg) ? (float) selfDeg : (float) selfDeg + 180;
        Angle correctedNodeAngle = AngleExtra.fromDegrees(correctedNodeDeg);

        float correctedOtherDeg = Angle.similarFacing((float) otherDeg, reverseGeoAngleDeg) ? (float) otherDeg : (float) otherDeg + 180;
        Angle correctedOtherAngle = AngleExtra.fromDegrees(correctedOtherDeg);

        double nodeSpeedMs = oldRail.getSpeedLimitMetersPerMillisecond(nodePos);
        double otherSpeedMs = oldRail.getSpeedLimitMetersPerMillisecond(otherPos);
        long nodeSpeedKmh = (long) Math.round(nodeSpeedMs * 3600000.0);
        long otherSpeedKmh = (long) Math.round(otherSpeedMs * 3600000.0);

        try {
            return Rail.newRail(
                    nodePos, correctedNodeAngle, otherPos, correctedOtherAngle,
                    oldRail.railMath.getShape(),
                    oldRail.railMath.getVerticalRadius(),
                    new ObjectArrayList<>(oldRail.getStyles()),
                    nodeSpeedKmh, otherSpeedKmh,
                    oldRail.isPlatform(),
                    oldRail.isSiding(),
                    oldRail.canAccelerate(),
                    oldRail.canConnectRemotely(),
                    true,
                    oldRail.getTransportMode()
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static double normalize(double angle) {
        angle = angle % 360.0D;
        if (angle < 0) angle += 360.0D;
        return angle;
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
        super.writeCompoundTag(tag);
        if (isBound()) {
            // 【重要修正】保存時に角度を 0〜180° の範囲に正規化する
            // これにより、コピーして設置した際も、意図した傾き（例: 30°）が正しく復元される
            double normalizedAngle = angleDegrees % 180.0;
            if (normalizedAngle < 0.0) {
                normalizedAngle += 180.0;
            }
            tag.putDouble(KEY_ANGLE, normalizedAngle);
        }
    }
}