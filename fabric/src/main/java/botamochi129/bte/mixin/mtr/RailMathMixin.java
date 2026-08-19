package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.BezierCurve;
import botamochi129.bte.mod.data.CantContext;
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
    @Unique private double bte$startRad = 0, bte$endRad = 0;
    @Unique private Vector bte$startPos = null, bte$endPos = null;
    @Unique private double bte$savedVerticalRadius = 0;
    @Unique private Rail.Shape bte$savedShape = Rail.Shape.QUADRATIC;
    @Unique private double bte$startRoll = 0, bte$endRoll = 0;

    @Inject(method = "<init>(Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Rail$Shape;D)V", at = @At("RETURN"))
    private void bte$capturePositions(Position position1, Angle angle1, Position position2, Angle angle2, Rail.Shape shape, double verticalRadius, CallbackInfo ci) {
        try {
            // ★ 最重要: World や BlockEntity は一切参照しない！ (非同期スレッドでのクラッシュを防ぐ)
            // 代わりに、渡された Angle が「MTR標準の22.5度スナップではない（＝BTEの自由角度）」かどうかで判定する

            boolean isPhantom1 = angle1 != null && (angle1.ordinal() < 0 || Math.abs(angle1.angleDegrees % 22.5) > 0.01);
            boolean isPhantom2 = angle2 != null && (angle2.ordinal() < 0 || Math.abs(angle2.angleDegrees % 22.5) > 0.01);

            // 両方ともMTR標準ノード（22.5度の倍数）なら、MTR標準の RailMath に完全に委譲する
            if (!isPhantom1 && !isPhantom2) {
                return;
            }

            // 片方でも自由角度（Phantom Angle）なら、BTEのベジェを適用
            double startRad = angle1 != null ? Math.toRadians(angle1.angleDegrees) : 0;
            double endRad = angle2 != null ? Math.toRadians(angle2.angleDegrees) : 0;

            Vector startVec = new Vector(position1.getX() + 0.5, position1.getY(), position1.getZ() + 0.5);
            Vector endVec = new Vector(position2.getX() + 0.5, position2.getY(), position2.getZ() + 0.5);

            this.bte$enableBezier(startVec, startRad, endVec, endRad, verticalRadius, shape);
            // オフセットやカントは、クライアント側の RenderRailsMixin が毎フレーム上書きするため、ここでは 0 のままでOK

        } catch (Throwable t) {
            // 絶対にパス生成スレッドをクラッシュさせない
        }
    }

    @Unique private BezierCurve bte$getActiveCurve() { return (bte$isBezierEnabled && bte$bezierCurve != null) ? bte$bezierCurve : null; }

    @Override public void bte$enableBezier(Vector startPos, double startRad, Vector endPos, double endRad, double verticalRadius, Rail.Shape shape) {
        this.bte$startPos = startPos; this.bte$endPos = endPos; this.bte$startRad = startRad; this.bte$endRad = endRad;
        this.bte$savedVerticalRadius = verticalRadius; this.bte$savedShape = shape;
        this.bte$bezierCurve = new BezierCurve(startPos, startRad, endPos, endRad, verticalRadius, shape);
        this.bte$isBezierEnabled = true;
    }
    @Override public boolean bte$isBezierEnabled() { return bte$isBezierEnabled; }
    @Override public BezierCurve bte$getCurve() { return bte$getActiveCurve(); }
    @Override public double bte$getStartRoll() { return bte$startRoll; }
    @Override public double bte$getEndRoll() { return bte$endRoll; }
    @Override public void bte$setRoll(double startRoll, double endRoll) { this.bte$startRoll = startRoll; this.bte$endRoll = endRoll; }

    // ★ サーバー側のパス計算（距離・座標）もベジェ曲線に合わせる
    // これにより、サーバー側もクライアント側も同じベジェの長さを認識する
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
        if (curve != null) cir.setReturnValue(curve.getLength());
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bte$render(RailMath.RenderRail callback, double interval, float offsetRadius1, float offsetRadius2, CallbackInfo ci) {
        // render はクライアント側のメインスレッドで呼ばれるので安全
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT) return;

        BezierCurve curve = bte$getActiveCurve();
        if (curve != null) {
            ci.cancel();
            double totalLength = curve.getLength();
            if (totalLength <= 0) return;
            double count = totalLength;
            double increment = count < 0.5 || interval <= 0 ? 0.5 : count / Math.round(count) * interval;
            double prevX1 = 0, prevZ1 = 0, prevX2 = 0, prevZ2 = 0, prevY = 0;
            boolean first = true;
            for (double i = 0.0; i < count + increment - 0.1; i += increment) {
                double t = curve.getTForDistance(i);
                Vector center = curve.getPoint(t);
                Vector tangent = curve.getTangent(t);
                Vector dir = new Vector(tangent.x(), 0, tangent.z()).normalize();
                Vector normal = new Vector(-dir.z(), 0, dir.x());
                double ratio = i / totalLength;
                double currentRoll = this.bte$startRoll + (this.bte$endRoll - this.bte$startRoll) * ratio;
                double x1 = center.x() + normal.x() * offsetRadius2, z1 = center.z() + normal.z() * offsetRadius2;
                double x2 = center.x() - normal.x() * offsetRadius1, z2 = center.z() - normal.z() * offsetRadius1;
                double y = center.y();
                if (!first) {
                    CantContext.set(new CantContext.CantData(currentRoll, center.x(), center.y(), center.z(), dir.x(), dir.z()));
                    try { callback.renderRail(prevX1, prevZ1, prevX2, prevZ2, x1, z1, x2, z2, prevY, y); } finally { CantContext.clear(); }
                } else { first = false; }
                prevX1 = x1; prevZ1 = z1; prevX2 = x2; prevZ2 = z2; prevY = y;
            }
        }
    }
}