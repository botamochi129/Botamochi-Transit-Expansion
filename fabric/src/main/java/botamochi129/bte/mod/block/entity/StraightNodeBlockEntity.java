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

    private static final String KEY_ANGLE = "angle";
    private static final double UNBOUND_SENTINEL = -129129.0D;

    // 内部データは 0〜360度 で管理する（技術的に必須）
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

    public boolean isConnected() {
        World world = getWorld2();
        if (world == null) return false;
        BlockState state = world.getBlockState(getPos2());
        return IBlock.getStatePropertySafe(state, BlockNode.IS_CONNECTED);
    }

    public void bind(StraightNodeBlockEntity other) {
        BlockPos thi = getPos2();
        BlockPos oth = other.getPos2();

        boolean selfBound = this.isBound();
        boolean otherBound = other.isBound();

        if (!selfBound && !otherBound) {
            double straightAngle = calculateStraightAngle(thi, oth);
            this.bind(straightAngle);
            other.bind(straightAngle);
        } else if (!selfBound && otherBound) {
            double smoothAngle = calculateSmoothCurveAngle(oth, other.getAngleDegrees(), thi);
            this.bind(smoothAngle);
        } else if (selfBound && !otherBound) {
            double smoothAngle = calculateSmoothCurveAngle(thi, this.getAngleDegrees(), oth);
            other.bind(smoothAngle);
        } else {
            this.updateRailwayData();
            other.updateRailwayData();
        }
    }

    private static double calculateStraightAngle(BlockPos pos1, BlockPos pos2) {
        double dx = pos2.getX() - pos1.getX();
        double dz = pos2.getZ() - pos1.getZ();
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        return normalize(deg);
    }

    private static double calculateSmoothCurveAngle(BlockPos fixedPos, double fixedAngleDeg, BlockPos targetPos) {
        double dx = targetPos.getX() - fixedPos.getX();
        double dz = targetPos.getZ() - fixedPos.getZ();
        double lineAngleRad = Math.atan2(dz, dx);

        double fixedAngleRad = Math.toRadians(fixedAngleDeg);
        double alpha = fixedAngleRad - lineAngleRad;

        double targetAngleRad = lineAngleRad - alpha;
        return normalize(Math.toDegrees(targetAngleRad));
    }

    public void bind(double angle) {
        this.angleDegrees = normalize(angle);
        markDirty2();
        syncBlockEntity();
        updateRailwayData();
    }

    public void unbind() {
        if (isConnected()) return;
        this.angleDegrees = UNBOUND_SENTINEL;
        markDirty2();
        syncBlockEntity();
    }

    public void updateRailwayData() {
        World world = getWorld2();
        if (world == null || world.isClient() || !isBound() || !isConnected()) return;

        Data data = LoaderImpl.getDataForWorld(world);
        if (data == null) return;

        Position fromPos = Init.blockPosToPosition(getPos2());
        Map<Position, Rail> map = data.positionsToRail.get(fromPos);
        if (map == null) return;

        ServerWorld serverWorld = LoaderImpl.toServerWorld(world);
        if (serverWorld == null) return;

        for (Map.Entry<Position, Rail> entry : map.entrySet()) {
            Position targetPos = entry.getKey();
            Rail oldRail = entry.getValue();

            BlockPos targetBlockPos = Init.positionToBlockPos(targetPos);
            org.mtr.mapping.holder.BlockEntity rawBe = world.getBlockEntity(targetBlockPos);

            double targetDeg;
            if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity straightBe && straightBe.isBound()) {
                targetDeg = straightBe.getAngleDegrees();
            } else {
                BlockState state = world.getBlockState(targetBlockPos);
                targetDeg = BlockNode.getAngle(state);
            }

            double selfDeg = getAngleDegrees();

            float geoAngle = (float) Math.toDegrees(Math.atan2(targetPos.getZ() - fromPos.getZ(), targetPos.getX() - fromPos.getX()));
            float reverseGeoAngle = (float) Math.toDegrees(Math.atan2(fromPos.getZ() - targetPos.getZ(), fromPos.getX() - targetPos.getX()));

            double correctedSelfDeg = Angle.similarFacing((float) selfDeg, geoAngle) ? selfDeg : selfDeg + 180;
            double correctedTargetDeg = Angle.similarFacing((float) targetDeg, reverseGeoAngle) ? targetDeg : targetDeg + 180;

            Angle angleFrom = AngleExtra.fromDegrees(correctedSelfDeg);
            Angle angleTarget = AngleExtra.fromDegrees(correctedTargetDeg);

            // 【修正】ご提示いただいた 15引数の newRail メソッドに完全一致させる
            Rail updatedRail = createUpdatedRail(fromPos, angleFrom, targetPos, angleTarget, oldRail);

            if (updatedRail != null && updatedRail.railMath != null && updatedRail.railMath instanceof IRailMathExtra mathExtra) {
                double startRad = Math.toRadians(correctedSelfDeg);
                double endRad = Math.toRadians(correctedTargetDeg);

                // 【修正】MTR のレール計算はブロック中心 (+0.5) を基準にするため、ここでオフセットを適用する
                Vector startVec = new Vector(fromPos.getX() + 0.5, fromPos.getY(), fromPos.getZ() + 0.5);
                Vector endVec = new Vector(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);

                mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad);

                data.rails.remove(oldRail);
                data.rails.add(updatedRail);
                data.positionsToRail.get(fromPos).put(targetPos, updatedRail);
                data.positionsToRail.get(targetPos).put(fromPos, updatedRail);
            }

            PacketUpdateData.sendDirectlyToServerRail(serverWorld, updatedRail);
        }

        syncBlockEntity();
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

    /**
     * RailSchema の canHaveSignal フィールドをリフレクションで取得する
     * Rail クラスにゲッターが公開されていない場合のフォールバック
     */
    private static boolean getCanHaveSignal(Rail rail) {
        try {
            // RailSchema の canHaveSignal フィールドにアクセス
            java.lang.reflect.Field field = rail.getClass().getSuperclass().getDeclaredField("canHaveSignal");
            field.setAccessible(true);
            return field.getBoolean(rail);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 【修正】ご提示いただいた 15引数の Rail.newRail シグネチャに完全一致
     */
    private Rail createUpdatedRail(Position fromPos, Angle angleFrom, Position targetPos, Angle angleTarget, Rail origin) {
        try {
            return Rail.newRail(
                    fromPos, angleFrom,
                    targetPos, angleTarget,
                    origin.railMath.getShape(),
                    origin.railMath.getVerticalRadius(),
                    new ObjectArrayList<>(origin.getStyles()),
                    (long) Math.round(origin.getSpeedLimitMetersPerMillisecond(fromPos) * 3600000.0),
                    (long) Math.round(origin.getSpeedLimitMetersPerMillisecond(targetPos) * 3600000.0),
                    origin.isPlatform(),
                    origin.isSiding(),
                    origin.canAccelerate(),
                    origin.canConnectRemotely(),
                    getCanHaveSignal(origin),
                    origin.getTransportMode()
            );
        } catch (Exception e) {
            System.err.println("[BTE Error] Failed to create updated rail: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void readCompoundTag(CompoundTag tag) {
        super.readCompoundTag(tag);
        if (tag.contains(KEY_ANGLE)) {
            this.angleDegrees = tag.getDouble(KEY_ANGLE);
        } else {
            this.angleDegrees = UNBOUND_SENTINEL;
        }
    }

    @Override
    public void writeCompoundTag(CompoundTag tag) {
        super.writeCompoundTag(tag);
        if (isBound()) {
            tag.putDouble(KEY_ANGLE, angleDegrees);
        }
    }

    /**
     * 内部データは 0〜360度 で正規化する（similarFacing 補正のために必須）
     */
    public static double normalize(double angle) {
        angle = angle % 360.0D;
        if (angle < 0) angle += 360.0D;
        return angle;
    }
}