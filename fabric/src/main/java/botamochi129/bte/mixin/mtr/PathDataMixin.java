package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.ArcCurve;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PathData.class, remap = false)
public abstract class PathDataMixin {

    private static final Rail.Shape[] SHAPES = Rail.Shape.values();

    // ★ 修正: protected フィールドへのアクセスを避け、public なものだけ Shadow する
    @Shadow public abstract Position getOrderedPosition1();
    @Shadow public abstract Position getOrderedPosition2();

    // PathData の public final boolean reversePositions を Shadow
    @Shadow @Final public boolean reversePositions;

    @Shadow public abstract double getStartDistance();
    @Shadow public abstract double getEndDistance();

    /**
     * 経路区間ごとのカーブキャッシュ。
     * <p>
     * 旧実装は {@code getPosition} の度に {@code new BezierCurve(...)} を作り直していた。
     * コンストラクタが O(400) の弧長サンプリング、{@code getPosition} 自体が
     * 50x50 = 2500 回のベジェ評価を伴っていたため、列車の移動頻度で
     * 経路探索側が極端に重くなっていた。
     * <p>
     * {@code PathData} は MTR がレール単位でキャッシュして使い回すので、
     * インスタンス поле に 1 本だけ持っておけば十分効く。
     */
    @Unique private ArcCurve bte$curve = null;

    /** キャッシュが張られた {@code RAIL_MATH_DATA_MAP} 値（先頭 10 要素）の写し。 */
    @Unique private double[] bte$curveSig = null;

    /** キャッシュ時の進行方向。{@code RAIL_MATH_DATA_MAP} の始点/終点と入れ替わるたびに変わる。 */
    @Unique private boolean bte$curveReversed = false;

    @Inject(method = "getPosition(D)Lorg/mtr/core/tool/Vector;", at = @At("HEAD"), cancellable = true)
    private void bte$overrideGetPosition(double rawValue, CallbackInfoReturnable<Vector> cir) {
        // ★ reversePositions を使って、常に「進行方向の始点 (startPosition)」と「終点 (endPosition)」を復元する
        Position p1 = this.reversePositions ? this.getOrderedPosition2() : this.getOrderedPosition1();
        Position p2 = this.reversePositions ? this.getOrderedPosition1() : this.getOrderedPosition2();

        if (p1 == null || p2 == null) return;

        double[] bezierData = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(StraightNodeBlockEntity.railMathKey(p1, p2));
        if (bezierData == null || bezierData.length < 14) return;

        long startBlockX = (long) bezierData[10];
        long startBlockZ = (long) bezierData[11];
        long endBlockX = (long) bezierData[12];
        long endBlockZ = (long) bezierData[13];

        // p1 (進行方向の始点) が Map の始点ブロックと一致するかで向きを決める
        boolean reversed;
        double startRad, endRad;
        if (p1.getX() == startBlockX && p1.getZ() == startBlockZ) {
            reversed = false;
            startRad = bezierData[6];
            endRad = bezierData[7];
        } else if (p1.getX() == endBlockX && p1.getZ() == endBlockZ) {
            // p1 が Map の終点ブロックと一致する場合 (復路など)
            // カーブの始点・終点を入れ替えて、進行方向に合わせる
            reversed = true;
            startRad = bezierData[7];
            endRad = bezierData[6];
        } else {
            return; // ブロック座標が一致しない場合はMTR標準に任せる
        }

        ArcCurve curve = bte$resolveCurve(bezierData, reversed, startRad, endRad);
        if (curve == null) return;

        final double bezierLength = curve.getLength();
        if (bezierLength <= 0) return;

        double mtrLength = this.getEndDistance() - this.getStartDistance();
        if (mtrLength <= 0) mtrLength = bezierLength;

        double ratio = Math.max(0, Math.min(rawValue, mtrLength)) / mtrLength;
        cir.setReturnValue(curve.getPosition(ratio * bezierLength));
    }

    /**
     * 入力が同じならキャッシュを返す。角度・オフセットが編集されて
     * {@code RAIL_MATH_DATA_MAP} の中身が変わったときだけ作り直す。
     */
    @Unique
    private ArcCurve bte$resolveCurve(double[] d, boolean reversed, double startRad, double endRad) {
        double[] sig = bte$curveSig;
        if (bte$curve != null && sig != null
                && bte$curveReversed == reversed
                && sig[6] == startRad && sig[7] == endRad
                && sig[0] == d[0] && sig[1] == d[1] && sig[2] == d[2]
                && sig[3] == d[3] && sig[4] == d[4] && sig[5] == d[5]
                && sig[8] == d[8] && sig[9] == d[9]) {
            return bte$curve;
        }

        int shapeOrdinal = (int) d[9];
        Rail.Shape shape = (shapeOrdinal >= 0 && shapeOrdinal < SHAPES.length) ? SHAPES[shapeOrdinal] : Rail.Shape.QUADRATIC;

        Vector startPos = new Vector(d[0], d[1], d[2]);
        Vector endPos = new Vector(d[3], d[4], d[5]);

        bte$curve = reversed
                ? new ArcCurve(endPos, endRad, startPos, startRad, d[8], shape)
                : new ArcCurve(startPos, startRad, endPos, endRad, d[8], shape);
        bte$curveReversed = reversed;

        if (sig == null) {
            sig = new double[10];
            bte$curveSig = sig;
        }
        System.arraycopy(d, 0, sig, 0, 10);
        return bte$curve;
    }
}
