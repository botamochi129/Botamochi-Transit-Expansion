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
        this.bte$startPos = new Vector(position1.getX(), position1.getY(), position1.getZ());
        this.bte$endPos = new Vector(position2.getX(), position2.getY(), position2.getZ());
        this.bte$savedVerticalRadius = verticalRadius;
        this.bte$savedShape = shape;

        long x1 = Math.min((long) bte$startPos.x(), (long) bte$endPos.x());
        long y1 = Math.min((long) bte$startPos.y(), (long) bte$endPos.y());
        long z1 = Math.min((long) bte$startPos.z(), (long) bte$endPos.z());
        long x2 = Math.max((long) bte$startPos.x(), (long) bte$endPos.x());
        long y2 = Math.max((long) bte$startPos.y(), (long) bte$endPos.y());
        long z2 = Math.max((long) bte$startPos.z(), (long) bte$endPos.z());

        // キーは整数座標
        String key = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;

        double[] existingData = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(key);
        if (existingData != null && existingData.length >= 10) {
            // 既存データがあれば、MTRが計算した最新の勾配・Shapeで上書き
            existingData[8] = verticalRadius;
            existingData[9] = shape.ordinal();
        } else {
            // 【追加】新規レール（MTR標準UIで作られた等）の場合、デフォルト値で新規登録
            // これにより、MTR標準のレールもベジェ曲線化の候補に入る
            double[] newData = new double[]{
                    (double) position1.getX() + 0.5, position1.getY(), (double) position1.getZ() + 0.5,
                    (double) position2.getX() + 0.5, position2.getY(), (double) position2.getZ() + 0.5,
                    0.0, 0.0, // 角度はデフォルト（後で RenderRails が更新）
                    verticalRadius, shape.ordinal()
            };
            StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.put(key, newData);
        }
    }

    @Unique
    private BezierCurve bte$getActiveCurve() {
        if (bte$isBezierEnabled && bte$bezierCurve != null) {
            return bte$bezierCurve;
        }

        if (this.bte$startPos != null && this.bte$endPos != null) {
            long x1 = Math.min((long) bte$startPos.x(), (long) bte$endPos.x());
            long y1 = Math.min((long) bte$startPos.y(), (long) bte$endPos.y());
            long z1 = Math.min((long) bte$startPos.z(), (long) bte$endPos.z());
            long x2 = Math.max((long) bte$startPos.x(), (long) bte$endPos.x());
            long y2 = Math.max((long) bte$startPos.y(), (long) bte$endPos.y());
            long z2 = Math.max((long) bte$startPos.z(), (long) bte$endPos.z());

            String railMathKey = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;
            double[] data = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(railMathKey);

            // 配列サイズ 10 (x,y,z * 2, startRad, endRad, vRad, shape)
            if (data != null && data.length >= 10) {
                Vector startPos = new Vector(data[0], data[1], data[2]);
                Vector endPos = new Vector(data[3], data[4], data[5]);
                double startRad = data[6];
                double endRad = data[7];
                double verticalRadius = data[8];

                Rail.Shape shape = Rail.Shape.QUADRATIC;
                int shapeOrdinal = (int) data[9];
                for (Rail.Shape s : Rail.Shape.values()) {
                    if (s.ordinal() == shapeOrdinal) {
                        shape = s;
                        break;
                    }
                }

                return new BezierCurve(startPos, startRad, endPos, endRad, verticalRadius, shape);
            }
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

        long x1 = Math.min((long) startPos.x(), (long) endPos.x());
        long y1 = Math.min((long) startPos.y(), (long) endPos.y());
        long z1 = Math.min((long) startPos.z(), (long) endPos.z());
        long x2 = Math.max((long) startPos.x(), (long) endPos.x());
        long y2 = Math.max((long) startPos.y(), (long) endPos.y());
        long z2 = Math.max((long) startPos.z(), (long) endPos.z());
        String key = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;

        // 配列サイズ 10 で登録
        double[] dataToSave = new double[]{
                startPos.x(), startPos.y(), startPos.z(),
                endPos.x(), endPos.y(), endPos.z(),
                startRad, endRad,
                verticalRadius,
                shape.ordinal()
        };
        StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.put(key, dataToSave);
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