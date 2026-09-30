package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.ArcCurve;
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

    private static final Rail.Shape[] SHAPES = Rail.Shape.values();

    @Unique private ArcCurve bte$arcCurve = null;
    @Unique private boolean bte$isBezierEnabled = false;
    @Unique private double bte$startRad = 0;
    @Unique private double bte$endRad = 0;
    @Unique private Vector bte$startPos = null;
    @Unique private Vector bte$endPos = null;
    @Unique private double bte$savedVerticalRadius = 0;
    @Unique private Rail.Shape bte$savedShape = Rail.Shape.QUADRATIC;

    /** レンダーループ用ワークバッファ。頂点ごとに Vector を作らないようにする。 */
    @Unique private double[] bte$renderScratch = null;

    @Inject(
            method = "<init>(Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Position;Lorg/mtr/core/tool/Angle;Lorg/mtr/core/data/Rail$Shape;D)V",
            at = @At("RETURN")
    )
    private void bte$capturePositions(
            Position position1, Angle angle1, Position position2, Angle angle2, Rail.Shape shape, double verticalRadius, CallbackInfo ci
    ) {
        double[] existingData = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(StraightNodeBlockEntity.railMathKey(position1, position2));

        // Map にデータがある = BTE ノードが関与しているレールだけ curves を使う。
        // 無い場合は何もしず MTR 純正の計算に委譲する（標準レールの軌道を守る）。
        if (existingData != null && existingData.length >= 14) {
            double vRad = existingData[8];
            int shapeOrdinal = (int) existingData[9];
            Rail.Shape s = (shapeOrdinal >= 0 && shapeOrdinal < SHAPES.length) ? SHAPES[shapeOrdinal] : Rail.Shape.QUADRATIC;

            this.bte$enableBezier(
                    new Vector(existingData[0], existingData[1], existingData[2]),
                    existingData[6],
                    new Vector(existingData[3], existingData[4], existingData[5]),
                    existingData[7],
                    vRad,
                    s
            );

            // MTR が保存した実値を優先させる（既存挙動を維持）
            if (existingData[8] != verticalRadius) existingData[8] = verticalRadius;
            if ((int) existingData[9] != shape.ordinal()) existingData[9] = shape.ordinal();
        }
    }

    @Unique
    private ArcCurve bte$getActiveCurve() {
        if (bte$isBezierEnabled && bte$arcCurve != null) {
            return bte$arcCurve;
        }
        return null;
    }

    @Override
    public void bte$enableBezier(Vector startPos, double startRad, Vector endPos, double endRad, double verticalRadius, Rail.Shape shape) {
        if (this.bte$isBezierEnabled && this.bte$arcCurve != null
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
        this.bte$arcCurve = new ArcCurve(startPos, startRad, endPos, endRad, verticalRadius, shape);
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
        ArcCurve curve = bte$getActiveCurve();
        if (curve != null) {
            double totalLength = curve.getLength();
            double clampedValue = Math.max(0, Math.min(rawValue, totalLength));
            cir.setReturnValue(curve.getPosition(reverse ? totalLength - clampedValue : clampedValue));
        }
    }

    @Inject(method = "getLength()D", at = @At("HEAD"), cancellable = true)
    private void bte$getLength(CallbackInfoReturnable<Double> cir) {
        ArcCurve curve = bte$getActiveCurve();
        if (curve != null) {
            cir.setReturnValue(curve.getLength());
        }
    }

    /**
     * 本家 {@code RailMath#renderSegment} の構造をそのまま踏襲する。
     *
     * <p>本家の {@code RenderRail#renderRail} は
     * {@code (x1, z1, x2, z2, x3, z3, x4, z4, y1, y2)} の <b>10 引数</b>で、
     * 前の頂点 2 点・現在の頂点 2 点の XZ と Y のみを渡す（各頂点の Y は持たない）。
     * 三角形ストリップのねじれを防ぐために {@code prev1 = c2; prev2 = c1} と
     * 入れ替えるのも本家と同じ。
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bte$render(RailMath.RenderRail callback, double interval, float offsetRadius1, float offsetRadius2, CallbackInfo ci) {
        ArcCurve curve = bte$getActiveCurve();
        if (curve == null) return;

        final double count = curve.getLength();
        if (count <= 0) {
            ci.cancel();
            return;
        }

        // ★ 本家 renderSegment と同一の式。判定に使うのは offsetRadius1 であって
        //   interval ではない。本家 renderRailStandard は
        //   render(callback, 0.5, -railWidth, +railWidth) を呼ぶので offsetRadius1 は必ず負で、
        //   結果として刻み幅は常に 0.5 に固定される。
        //   interval で判定すると count / Math.round(count) * 0.5 となり
        //   レール長により 0.45〜0.55 に揺れて、レール模型の配置が乱れる。
        final double increment = (count < 0.5 || offsetRadius1 <= 0) ? 0.5 : count / Math.round(count) * offsetRadius1;

        double[] buf = bte$renderScratch;
        if (buf == null) {
            // Mixin のフィールド初期化に依存せず、初回描画で確保する
            buf = new double[4];
            bte$renderScratch = buf;
        }
        double prevX1 = 0, prevZ1 = 0, prevX2 = 0, prevZ2 = 0, prevY = 0.0;
        boolean hasPrev = false;

        for (double i = 0.0; i < count + increment - 0.1; i += increment) {
            // corner1 = offsetRadius1 側、corner2 = offsetRadius2 側（本家と同じ割り当て）。
            // theta が共通なので 1 頂点あたり三角関数は 2 回ではなく 1 回で済む。
            final double y = curve.getPositionXZPair(i, offsetRadius1, offsetRadius2, buf);
            final double c1x = buf[0], c1z = buf[1];
            final double c2x = buf[2], c2z = buf[3];

            if (hasPrev) {
                callback.renderRail(
                        prevX1, prevZ1,
                        prevX2, prevZ2,
                        c1x, c1z,
                        c2x, c2z,
                        prevY, y
                );
            }

            // 本家と同じ Swap（triangle strip のねじれ防止）
            prevX1 = c2x; prevZ1 = c2z;
            prevX2 = c1x; prevZ2 = c1z;
            prevY = y;
            hasPrev = true;
        }

        ci.cancel();
    }
}
