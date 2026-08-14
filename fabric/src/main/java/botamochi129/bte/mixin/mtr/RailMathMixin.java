package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.BezierCurve;
import botamochi129.bte.mod.data.IRailMathExtra;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RailMath.class, remap = false)
public abstract class RailMathMixin implements IRailMathExtra {

    @Unique private BezierCurve bte$bezierCurve = null;
    @Unique private boolean bte$isBezierEnabled = false;
    @Unique private double bte$startRad = 0;
    @Unique private double bte$endRad = 0;
    @Unique private Vector bte$startPos = null;
    @Unique private Vector bte$endPos = null;
    @Unique private double bte$savedVerticalRadius = 0;
    @Unique private Rail.Shape bte$savedShape = Rail.Shape.QUADRATIC;

    @Inject(
            method = "<init>(Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Rail$Shape;D)V",
            at = @At("RETURN")
    )
    private void bte$capturePositions(
            Position position1, Angle angle1, Position position2, Angle angle2, Rail.Shape shape, double verticalRadius, CallbackInfo ci
    ) {
        long x1 = Math.min(position1.getX(), position2.getX());
        long y1 = Math.min(position1.getY(), position2.getY());
        long z1 = Math.min(position1.getZ(), position2.getZ());
        long x2 = Math.max(position1.getX(), position2.getX());
        long y2 = Math.max(position1.getY(), position2.getY());
        long z2 = Math.max(position1.getZ(), position2.getZ());
        String key = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;

        double[] existingData = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(key);

        if (existingData != null && existingData.length >= 14) {
            Vector startPos = new Vector(existingData[0], existingData[1], existingData[2]);
            Vector endPos = new Vector(existingData[3], existingData[4], existingData[5]);
            double startRad = existingData[6];
            double endRad = existingData[7];
            double vRad = existingData[8];

            Rail.Shape s = Rail.Shape.QUADRATIC;
            int shapeOrdinal = (int) existingData[9];
            for (Rail.Shape rs : Rail.Shape.values()) {
                if (rs.ordinal() == shapeOrdinal) { s = rs; break; }
            }

            this.bte$enableBezier(startPos, startRad, endPos, endRad, vRad, s);
            existingData[8] = verticalRadius;
            existingData[9] = shape.ordinal();

        } else if (existingData != null && existingData.length >= 10) {
            double[] newData = new double[14];
            System.arraycopy(existingData, 0, newData, 0, 10);
            newData[10] = position1.getX();
            newData[11] = position1.getZ();
            newData[12] = position2.getX();
            newData[13] = position2.getZ();
            StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.put(key, newData);

            Vector startPos = new Vector(newData[0], newData[1], newData[2]);
            Vector endPos = new Vector(newData[3], newData[4], newData[5]);
            double startRad = newData[6];
            double endRad = newData[7];
            this.bte$enableBezier(startPos, startRad, endPos, endRad, verticalRadius, shape);

        } else {
            // ★ 修正: デフォルト0度ではなく、Rail生成時に渡されたAngleを使用する
            // これにより、MTR標準ノードの角度も正しくベジェデータに反映される
            double startDeg = angle1 != null ? angle1.angleDegrees : 0.0;
            double endDeg = angle2 != null ? angle2.angleDegrees : 0.0;

            double[] newData = new double[]{
                    position1.getX() + 0.5, position1.getY(), position1.getZ() + 0.5,
                    position2.getX() + 0.5, position2.getY(), position2.getZ() + 0.5,
                    Math.toRadians(startDeg), Math.toRadians(endDeg), // ★ Angleをラジアンに変換
                    verticalRadius, shape.ordinal(),
                    (double) position1.getX(), (double) position1.getZ(),
                    (double) position2.getX(), (double) position2.getZ()
            };
            StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.put(key, newData);
        }
    }

    @Unique
    private BezierCurve bte$getActiveCurve() {
        if (bte$isBezierEnabled && bte$bezierCurve != null) {
            return bte$bezierCurve;
        }
        return null;
    }

    @Override
    public void bte$enableBezier(Vector startPos, double startRad, Vector endPos, double endRad, double verticalRadius, Rail.Shape shape) {
        if (this.bte$isBezierEnabled && this.bte$bezierCurve != null
                && this.bte$startRad == startRad && this.bte$endRad == endRad
                && this.bte$startPos != null && this.bte$startPos.equals(startPos)
                && this.bte$endPos != null && this.bte$endPos.equals(endPos)
                && this.bte$savedVerticalRadius == verticalRadius
                && this.bte$savedShape == shape) {
            return;
        }
        this.bte$startPos = startPos;
        this.bte$endPos = endPos;
        this.bte$startRad = startRad;
        this.bte$endRad = endRad;
        this.bte$savedVerticalRadius = verticalRadius;
        this.bte$savedShape = shape;
        this.bte$bezierCurve = new BezierCurve(startPos, startRad, endPos, endRad, verticalRadius, shape);
        this.bte$isBezierEnabled = true;
    }

    @Override
    public boolean bte$isBezierEnabled() { return bte$isBezierEnabled; }
    @Override
    public double bte$getStartRad() { return bte$startRad; }
    @Override
    public double bte$getEndRad() { return bte$endRad; }

    @Inject(method = "getPosition(DZ)Lorg/mtr/core/tool/Vector;", at = @At("HEAD"), cancellable = true)
    private void bte$modifyPosition(double rawValue, boolean reverse, CallbackInfoReturnable<Vector> cir) {
        BezierCurve curve = bte$getActiveCurve();
        if (curve != null) {
            double totalLength = curve.getLength();
            double clampedValue = Math.max(0, Math.min(rawValue, totalLength));
            double targetValue = reverse ? totalLength - clampedValue : clampedValue;
            cir.setReturnValue(curve.getPosition(targetValue));
        }
    }

    @Inject(method = "getLength()D", at = @At("HEAD"), cancellable = true)
    private void bte$getLength(CallbackInfoReturnable<Double> cir) {
        BezierCurve curve = bte$getActiveCurve();
        if (curve != null) {
            cir.setReturnValue(curve.getLength());
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bte$render(RailMath.RenderRail callback, double interval, float offsetRadius1, float offsetRadius2, CallbackInfo ci) {
        BezierCurve curve = bte$getActiveCurve();
        if (curve != null) {
            double totalLength = curve.getLength();
            if (totalLength <= 0) return;

            double count = totalLength;
            double increment = count < 0.5 || interval <= 0 ? 0.5 : count / Math.round(count) * interval;

            Vector previousCorner1 = null;
            Vector previousCorner2 = null;
            double previousY = 0.0;

            for (double i = 0.0; i < count + increment - 0.1; i += increment) {
                double t = curve.getTForDistance(i);
                Vector bezierPos = curve.getPoint(t);
                double y = bezierPos.y();

                Vector tangent = curve.getTangent(t);
                Vector dir = new Vector(tangent.x(), 0, tangent.z()).normalize();
                Vector normal = new Vector(-dir.z(), 0, dir.x());

                Vector corner1 = new Vector(
                        bezierPos.x() + normal.x() * offsetRadius2, y, bezierPos.z() + normal.z() * offsetRadius2
                );
                Vector corner2 = offsetRadius2 == offsetRadius1 ? corner1 : new Vector(
                        bezierPos.x() + normal.x() * offsetRadius1, y, bezierPos.z() + normal.z() * offsetRadius1
                );

                if (previousCorner1 != null) {
                    callback.renderRail(
                            previousCorner1.x(), previousCorner1.z(), previousCorner2.x(), previousCorner2.z(),
                            corner1.x(), corner1.z(), corner2.x(), corner2.z(), previousY, y
                    );
                }
                previousCorner1 = corner2;
                previousCorner2 = corner1;
                previousY = y;
            }
            ci.cancel();
        }
    }
}