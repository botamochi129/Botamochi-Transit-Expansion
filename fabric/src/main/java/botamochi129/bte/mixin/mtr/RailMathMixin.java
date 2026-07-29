package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.BezierCurve;
import botamochi129.bte.mod.data.IRailMathExtra;
import org.mtr.core.data.RailMath;
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

    @Override
    public void bte$enableBezier(Vector startPos, double startRad, Vector endPos, double endRad) {
        // キャッシュチェック: パラメータが前回と同じなら BezierCurve を再生成しない
        if (this.bte$isBezierEnabled && this.bte$bezierCurve != null
                && this.bte$startRad == startRad && this.bte$endRad == endRad
                && this.bte$startPos != null && this.bte$startPos.equals(startPos)
                && this.bte$endPos != null && this.bte$endPos.equals(endPos)) {
            return; // 変更なし
        }

        this.bte$startPos = startPos;
        this.bte$endPos = endPos;
        this.bte$startRad = startRad;
        this.bte$endRad = endRad;
        this.bte$bezierCurve = new BezierCurve(startPos, startRad, endPos, endRad);
        this.bte$isBezierEnabled = true;
    }

    @Override
    public boolean bte$isBezierEnabled() { return bte$isBezierEnabled; }

    @Override
    public double bte$getStartRad() { return bte$startRad; }

    @Override
    public double bte$getEndRad() { return bte$endRad; }

    @Inject(method = "getPosition(DZ)Lorg/mtr/core/tool/Vector;", at = @At("HEAD"), cancellable = true)
    private void bte$getPosition(double rawValue, boolean reverse, CallbackInfoReturnable<Vector> cir) {
        if (bte$isBezierEnabled && bte$bezierCurve != null) {
            double totalLength = bte$bezierCurve.getLength();
            double clampedValue = Math.max(0, Math.min(rawValue, totalLength));
            double targetValue = reverse ? totalLength - clampedValue : clampedValue;

            // あなたの BezierCurve が距離から高精度で座標を返す
            cir.setReturnValue(bte$bezierCurve.getPosition(targetValue));
        }
    }

    @Inject(method = "getLength()D", at = @At("HEAD"), cancellable = true)
    private void bte$getLength(CallbackInfoReturnable<Double> cir) {
        if (bte$isBezierEnabled && bte$bezierCurve != null) {
            cir.setReturnValue(bte$bezierCurve.getLength());
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bte$render(RailMath.RenderRail callback, double interval, float offsetRadius1, float offsetRadius2, CallbackInfo ci) {
        if (bte$isBezierEnabled && bte$bezierCurve != null) {
            double totalLength = bte$bezierCurve.getLength();
            if (totalLength <= 0) return;

            // MTR本家の increment 計算ロジックに厳密に合わせる
            double count = totalLength;
            double increment = count < 0.5 || interval <= 0 ? 0.5 : count / Math.round(count) * interval;

            Vector previousCorner1 = null;
            Vector previousCorner2 = null;
            double previousY = 0.0;

            for (double i = 0.0; i < count + increment - 0.1; i += increment) {
                // 距離 i からパラメータ t を取得 (あなたの二分探索により高精度)
                double t = bte$bezierCurve.getTForDistance(i);
                Vector center = bte$bezierCurve.getPoint(t);

                // 接線ベクトルから X-Z 平面での法線を計算
                Vector tangent = bte$bezierCurve.getTangent(t);
                // Y成分を0にして水平方向の法線ベクトルにする (MTRの標準動作に合わせる)
                Vector dir = new Vector(tangent.x(), 0, tangent.z()).normalize();
                Vector normal = new Vector(-dir.z(), 0, dir.x());

                // オフセットを適用したコーナー座標を計算
                Vector corner1 = new Vector(
                        center.x() + normal.x() * offsetRadius2,
                        center.y(),
                        center.z() + normal.z() * offsetRadius2
                );
                Vector corner2 = offsetRadius2 == offsetRadius1 ? corner1 : new Vector(
                        center.x() + normal.x() * offsetRadius1,
                        center.y(),
                        center.z() + normal.z() * offsetRadius1
                );

                if (previousCorner1 != null) {
                    // 【確定】MTR 4.x の RenderRail インターフェース (10引数: x1, z1, x2, z2, x3, z3, x4, z4, y1, y2)
                    callback.renderRail(
                            previousCorner1.x(), previousCorner1.z(),
                            previousCorner2.x(), previousCorner2.z(),
                            corner1.x(), corner1.z(),
                            corner2.x(), corner2.z(),
                            previousY, center.y()
                    );
                }

                previousCorner1 = corner2;
                previousCorner2 = corner1;
                previousY = center.y();
            }

            ci.cancel();
        }
    }
}