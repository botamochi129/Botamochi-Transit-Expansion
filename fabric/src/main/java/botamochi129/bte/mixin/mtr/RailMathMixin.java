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

    // 【修正】verticalRadius を受け取るように変更
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
        // 【修正】BezierCurve のコンストラクタに verticalRadius を渡す
        this.bte$bezierCurve = new BezierCurve(startPos, startRad, endPos, endRad, verticalRadius);
        this.bte$isBezierEnabled = true;
    }

    @Override
    public boolean bte$isBezierEnabled() { return bte$isBezierEnabled; }

    @Override
    public double bte$getStartRad() { return bte$startRad; }

    @Override
    public double bte$getEndRad() { return bte$endRad; }

    // 【重要】CancellationException を回避するため、getPosition のフックは削除（または無効化）
    // 列車の移動計算などは、MTR標準の getPosition をそのまま使う（X-Zがベジェ曲線にならない可能性があるが、クラッシュは回避できる）
    // もし列車の移動もベジェ曲線に合わせたい場合は、@ModifyVariable を正しく使う必要があるが、まずは描画を優先する

    @Inject(method = "getLength()D", at = @At("HEAD"), cancellable = true)
    private void bte$getLength(CallbackInfoReturnable<Double> cir) {
        if (bte$isBezierEnabled && bte$bezierCurve != null) {
            cir.setReturnValue(bte$bezierCurve.getLength());
        }
    }

    // 描画処理の完全な乗っ取り
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bte$render(RailMath.RenderRail callback, double interval, float offsetRadius1, float offsetRadius2, CallbackInfo ci) {
        if (bte$isBezierEnabled && bte$bezierCurve != null) {
            double totalLength = bte$bezierCurve.getLength();
            if (totalLength <= 0) return;

            double count = totalLength;
            double increment = count < 0.5 || interval <= 0 ? 0.5 : count / Math.round(count) * interval;

            Vector previousCorner1 = null;
            Vector previousCorner2 = null;
            double previousY = 0.0;

            for (double i = 0.0; i < count + increment - 0.1; i += increment) {
                // 【修正】self.getPosition を呼ばず、BezierCurve から直接座標を取得
                double t = bte$bezierCurve.getTForDistance(i);
                Vector center = bte$bezierCurve.getPoint(t); // ここで Y座標も計算される
                double y = center.y();

                Vector tangent = bte$bezierCurve.getTangent(t);
                Vector dir = new Vector(tangent.x(), 0, tangent.z()).normalize();
                Vector normal = new Vector(-dir.z(), 0, dir.x());

                Vector corner1 = new Vector(
                        center.x() + normal.x() * offsetRadius2,
                        y,
                        center.z() + normal.z() * offsetRadius2
                );
                Vector corner2 = offsetRadius2 == offsetRadius1 ? corner1 : new Vector(
                        center.x() + normal.x() * offsetRadius1,
                        y,
                        center.z() + normal.z() * offsetRadius1
                );

                if (previousCorner1 != null) {
                    callback.renderRail(
                            previousCorner1.x(), previousCorner1.z(),
                            previousCorner2.x(), previousCorner2.z(),
                            corner1.x(), corner1.z(),
                            corner2.x(), corner2.z(),
                            previousY, y
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