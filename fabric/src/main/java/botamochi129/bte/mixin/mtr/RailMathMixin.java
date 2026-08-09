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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RailMath.class, remap = false)
public abstract class RailMathMixin implements IRailMathExtra {

    // 【追加】RailMath のバウンディングボックスフィールドを Shadow で取得
    @Shadow public long minX;
    @Shadow public long maxX;
    @Shadow public long minZ;
    @Shadow public long maxZ;

    @Unique private BezierCurve bte$bezierCurve = null;
    @Unique private boolean bte$isBezierEnabled = false;
    @Unique private double bte$startRad = 0;
    @Unique private double bte$endRad = 0;
    @Unique private Vector bte$startPos = null;
    @Unique private Vector bte$endPos = null;

    @Override
    public void bte$enableBezier(Vector startPos, double startRad, Vector endPos, double endRad, double verticalRadius) {
        if (this.bte$isBezierEnabled && this.bte$bezierCurve != null
                && this.bte$startRad == startRad && this.bte$endRad == endRad
                && this.bte$startPos != null && this.bte$startPos.equals(startPos)
                && this.bte$endPos != null && this.bte$endPos.equals(endPos)) {
            return;
        }
        this.bte$startPos = startPos;
        this.bte$endPos = endPos;
        this.bte$startRad = startRad;
        this.bte$endRad = endRad;
        this.bte$bezierCurve = new BezierCurve(startPos, startRad, endPos, endRad, verticalRadius);
        this.bte$isBezierEnabled = true;

        // 【超重要追加】クライアント側でも PathDataMixin がデータを見つけられるようにマップに登録する
        long x1 = Math.min((long) startPos.x(), (long) endPos.x());
        long y1 = Math.min((long) startPos.y(), (long) endPos.y());
        long z1 = Math.min((long) startPos.z(), (long) endPos.z());
        long x2 = Math.max((long) startPos.x(), (long) endPos.x());
        long y2 = Math.max((long) startPos.y(), (long) endPos.y());
        long z2 = Math.max((long) startPos.z(), (long) endPos.z());
        String key = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;

        double[] dataToSave = new double[]{
                startPos.x(), startPos.y(), startPos.z(),
                endPos.x(), endPos.y(), endPos.z(),
                startRad, endRad, verticalRadius
        };
        botamochi129.bte.mod.block.entity.StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.put(key, dataToSave);
    }

    @Override
    public boolean bte$isBezierEnabled() { return bte$isBezierEnabled; }
    @Override
    public double bte$getStartRad() { return bte$startRad; }
    @Override
    public double bte$getEndRad() { return bte$endRad; }

    // 【核心】ベジェ曲線を取得する統合メソッド
    @Unique
    private BezierCurve bte$getActiveCurve() {
        // 1. @Unique フィールドが有効な場合（クライアント側の描画用）
        if (bte$isBezierEnabled && bte$bezierCurve != null) {
            return bte$bezierCurve;
        }

        // 2. 静的マップにデータがある場合（サーバー側のシミュレーション用）
        String railMathKey = this.minX + "," + this.maxX + "," + this.minZ + "," + this.maxZ;
        //System.out.println("[BTE MIXIN] LOOKING UP RailMath Key: " + railMathKey + " on thread: " + Thread.currentThread().getName());

        double[] data = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(railMathKey);
        if (data != null) {
            //System.out.println("[BTE MIXIN] SUCCESS: Found data! Applying Bezier curve.");
            Vector startPos = new Vector(data[0], data[1], data[2]);
            Vector endPos = new Vector(data[3], data[4], data[5]);
            return new BezierCurve(startPos, data[6], endPos, data[7], data[8]);
        } else {
            //System.out.println("[BTE MIXIN] FAILED: Data NOT found. Using default MTR math.");
        }

        return null;
    }

    @ModifyArg(method = "<init>(Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Rail$Shape;D)V", at = @At("HEAD"), index = 1)
    private static Angle bte$modifyAngle1(Position position1, Angle angle1, Position position2, Angle angle2, Rail.Shape shape, double verticalRadius) {
        long x1 = Math.min(position1.getX(), position2.getX());
        long y1 = Math.min(position1.getY(), position2.getY());
        long z1 = Math.min(position1.getZ(), position2.getZ());
        long x2 = Math.max(position1.getX(), position2.getX());
        long y2 = Math.max(position1.getY(), position2.getY());
        long z2 = Math.max(position1.getZ(), position2.getZ());
        String key = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;
        double[] data = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(key);
        if (data != null) {
            return Angle.fromAngle((float) Math.toDegrees(data[6]));
        }
        return angle1;
    }

    // 【超重要追加】RailMath のコンストラクタをフックし、angle2 を自由角度に書き換える
    @ModifyArg(method = "<init>(Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Rail$Shape;D)V", at = @At("HEAD"), index = 3)
    private static Angle bte$modifyAngle2(Position position1, Angle angle1, Position position2, Angle angle2, Rail.Shape shape, double verticalRadius) {
        long x1 = Math.min(position1.getX(), position2.getX());
        long y1 = Math.min(position1.getY(), position2.getY());
        long z1 = Math.min(position1.getZ(), position2.getZ());
        long x2 = Math.max(position1.getX(), position2.getX());
        long y2 = Math.max(position1.getY(), position2.getY());
        long z2 = Math.max(position1.getZ(), position2.getZ());
        String key = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;
        double[] data = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(key);
        if (data != null) {
            return Angle.fromAngle((float) Math.toDegrees(data[7]));
        }
        return angle2;
    }

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