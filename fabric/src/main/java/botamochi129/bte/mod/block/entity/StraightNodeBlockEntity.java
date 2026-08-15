package botamochi129.bte.mod.block.entity;

import botamochi129.bte.mapping.LoaderImpl;
import botamochi129.bte.mod.data.IRailMathExtra;
import botamochi129.bte.mod.data.NodeGeometry;
import botamochi129.bte.mod.registry.Blocks;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
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

import java.util.Map;

public class StraightNodeBlockEntity extends BlockEntityExtension {

    private static final String KEY_ANGLE = "angle_degrees";
    private static final String KEY_OFFSET_X = "offset_x";
    private static final String KEY_OFFSET_Y = "offset_y";
    private static final String KEY_OFFSET_Z = "offset_z";

    public static final double UNBOUND_SENTINEL = -129129.0;

    public static final Map<String, double[]> RAIL_MATH_DATA_MAP = new java.util.concurrent.ConcurrentHashMap<>();

    private double angleDegrees = UNBOUND_SENTINEL;
    private boolean needsInitialUpdate = true;
    private int tickCount = 0;

    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double offsetZ = 0.0;

    public StraightNodeBlockEntity(BlockPos pos, BlockState state) {
        super(Blocks.STRAIGHT_NODE_BE.get(), pos, state);
    }

    public StraightNodeBlockEntity(BlockPos pos, BlockState state, double angle) {
        super(Blocks.STRAIGHT_NODE_BE.get(), pos, state);
        this.angleDegrees = angle;
    }

    public void tick() {
        World world = getWorld2();
        if (world == null || world.isClient() || !isBound()) return;

        tickCount++;
        if (needsInitialUpdate || tickCount >= 10) {
            tickCount = 0;
            needsInitialUpdate = false;
            updateConnectedRails(true);
        }
    }

    public double getAngleDegrees() {
        return angleDegrees;
    }

    public boolean isBound() {
        return angleDegrees != UNBOUND_SENTINEL;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public double getOffsetZ() {
        return offsetZ;
    }

    public void setOffset(double x, double y, double z) {
        this.offsetX = Math.max(-1.0, Math.min(1.0, x));
        this.offsetY = Math.max(-1.0, Math.min(1.0, y));
        this.offsetZ = Math.max(-1.0, Math.min(1.0, z));
        markDirty2();
        syncBlockEntity();
        updateBezierDataOnly();
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
        updateBezierDataOnly();
    }

    private void updateBezierDataOnly() {
        if (!isBound()) return;
        World world = getWorld2();
        if (world == null || world.isClient()) return;

        Data data = LoaderImpl.getDataForWorld(world);
        if (data == null) return;

        Position nodePos = Init.blockPosToPosition(getPos2());
        double selfAxis = this.angleDegrees; // 0~180 の軸

        Map<Position, Rail> railsAtPos = data.positionsToRail.get(nodePos);
        if (railsAtPos == null) return;

        for (Map.Entry<Position, Rail> entry : railsAtPos.entrySet()) {
            Position otherPos = entry.getKey();
            Rail rail = entry.getValue();
            if (rail == null || rail.railMath == null) continue;

            long x1 = Math.min(nodePos.getX(), otherPos.getX());
            long y1 = Math.min(nodePos.getY(), otherPos.getY());
            long z1 = Math.min(nodePos.getZ(), otherPos.getZ());
            long x2 = Math.max(nodePos.getX(), otherPos.getX());
            long y2 = Math.max(nodePos.getY(), otherPos.getY());
            long z2 = Math.max(nodePos.getZ(), otherPos.getZ());
            String railMathKey = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;

            BlockPos otherBlockPos = Init.positionToBlockPos(otherPos);
            double otherAxis = BlockNode.getAngle(world.getBlockState(otherBlockPos)); // MTR標準ノードも軸として扱う
            double otherOffX = 0, otherOffY = 0, otherOffZ = 0;

            BlockEntity rawBe = world.getBlockEntity(otherBlockPos);
            if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity snbe && snbe.isBound()) {
                otherAxis = snbe.getAngleDegrees(); // 0~180 の軸
                otherOffX = snbe.getOffsetX();
                otherOffY = snbe.getOffsetY();
                otherOffZ = snbe.getOffsetZ();
            }

            float geoAngleDeg = (float) Math.toDegrees(Math.atan2(
                    otherPos.getZ() - nodePos.getZ(), otherPos.getX() - nodePos.getX()));
            float reverseGeoAngleDeg = geoAngleDeg + 180.0f;

            // ★ 動的に最適な出口を選択 (軸から +0° か +180° かを決める)
            double selfExit = NodeGeometry.chooseBestExit(selfAxis, geoAngleDeg);
            double otherExit = NodeGeometry.chooseBestExit(otherAxis, reverseGeoAngleDeg);

            double startRad = Math.toRadians(selfExit);
            double endRad = Math.toRadians(otherExit);
            double verticalRadius = rail.railMath.getVerticalRadius();
            Rail.Shape shape = rail.railMath.getShape();

            Vector startVec = new Vector(
                    nodePos.getX() + 0.5 + this.offsetX, nodePos.getY() + this.offsetY, nodePos.getZ() + 0.5 + this.offsetZ
            );
            Vector endVec = new Vector(
                    otherPos.getX() + 0.5 + otherOffX, otherPos.getY() + otherOffY, otherPos.getZ() + 0.5 + otherOffZ
            );

            if (rail.railMath instanceof IRailMathExtra mathExtra) {
                mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad, verticalRadius, shape);
            }

            double[] dataToSave = new double[]{
                    startVec.x(), startVec.y(), startVec.z(),
                    endVec.x(), endVec.y(), endVec.z(),
                    startRad, endRad,
                    verticalRadius, shape.ordinal(),
                    nodePos.getX(), nodePos.getZ(),
                    otherPos.getX(), otherPos.getZ()
            };
            RAIL_MATH_DATA_MAP.put(railMathKey, dataToSave);
        }
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
            if (sw != null) sw.getChunkManager().markForUpdate(getPos2());

            LoaderImpl.sendBlockEntityUpdatePacket(world, getPos2());
        }
    }

    public void updateConnectedRails(boolean updateSimulation) {
        updateBezierDataOnly();
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

        if (tag.contains(KEY_OFFSET_X)) {
            offsetX = tag.getDouble(KEY_OFFSET_X);
            offsetY = tag.getDouble(KEY_OFFSET_Y);
            offsetZ = tag.getDouble(KEY_OFFSET_Z);
        } else {
            offsetX = offsetY = offsetZ = 0.0;
        }

        this.needsInitialUpdate = true;
    }

    @Override
    public void writeCompoundTag(CompoundTag tag) {
        super.writeCompoundTag(tag);
        if (isBound()) {
            // ★ 修正: % 180.0 に戻し、「軸」として保存する
            double normalizedAngle = angleDegrees % 180.0;
            if (normalizedAngle < 0.0) normalizedAngle += 180.0;
            tag.putDouble(KEY_ANGLE, normalizedAngle);
        }

        if (offsetX != 0.0 || offsetY != 0.0 || offsetZ != 0.0) {
            tag.putDouble(KEY_OFFSET_X, offsetX);
            tag.putDouble(KEY_OFFSET_Y, offsetY);
            tag.putDouble(KEY_OFFSET_Z, offsetZ);
        }
    }
}