package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.AngleExtra;
import botamochi129.bte.mod.data.RailAngleOverride;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.mod.Init;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link Rail#getStartAngle(Position)} を、BTE ノード軸から導出した値で上書きする。
 * <p>
 * MTR は {@code Rail.angle1/angle2} を {@code final} で持ち、経路探索
 * ({@code SidingPathFinder#getConnections}) は保存値のみを厳密一致 ({@code ==}) で比較する:
 * <pre>
 *   node.angle == rail.getStartAngle(node.position)
 * </pre>
 * 共有ノードで接続が成立する条件は「両レールの出発方向が厳密に180度差」であり、
 * BTE ノードから既存の MTR 標準レールが伸びている場合、その端の角度は
 * <b>標準ノードの軸 (45度刻み)</b> にスナップされなければならない。
 * <p>
 * 一方 BTE は自分の端だけ投影した自由角度 (例: D171.761) を保存していたため、
 * 標準ノード端で 8.239 度ずれて経路が切れていた。ノード軸を画面から変更した後も
 * 保存値は更新されないため、再描画なしでは永久に直らない。
 * <p>
 * {@code StraightNodeBlockEntity#updateBezierDataOnly()} は既に
 * {@code NodeGeometry.chooseBestExit} で両端の正しい角度を計算済み (-bezier 描画に使用している値)。
 * 本 Mixin はその値を同じループで publish し、経路探索と走行位置の整合性を確保する。
 * {@link botamochi129.bte.mod.data.RailAngleOverride} で上書きが不可能な場合は
 * 保存値にフォールバックするため、MTR 標準レール単体の挙動は変わらない。
 */
@Mixin(value = Rail.class, remap = false)
public abstract class RailStartAngleMixin implements RailAngleOverride {

    @Shadow
    protected abstract Position getPosition1();

    @Shadow
    protected abstract Position getPosition2();

    @Unique
    private Angle bte$angleOverride1;

    @Unique
    private Angle bte$angleOverride2;

    @Override
    public void bte$setAngleOverride(Position position, double angleDegrees) {
        if (position == null) return;
        if (position.equals(getPosition1())) {
            bte$angleOverride1 = AngleExtra.fromDegrees(angleDegrees);
        } else if (position.equals(getPosition2())) {
            bte$angleOverride2 = AngleExtra.fromDegrees(angleDegrees);
        }
    }

    @Override
    public void bte$clearAngleOverride(Position position) {
        if (position == null) return;
        if (position.equals(getPosition1())) {
            bte$angleOverride1 = null;
        } else if (position.equals(getPosition2())) {
            bte$angleOverride2 = null;
        }
    }

    /**
     * 上書きが失われていたら {@link StraightNodeBlockEntity#RAIL_MATH_DATA_MAP} から復元する。
     *
     * <p><b>これが点滅の根治策。</b> MTR は自前のデータ保存で {@code Rail} 実体を作り直すため、
     * {@link #bte$angleOverride1} / {@link #bte$angleOverride2} はそのたびに消える。
     * 再生成から {@code StraightNodeBlockEntity#drain()}（最大 4 tick）までは上書きが無い状態で
     * 経路探索が動き、<b>接続グラフが変わる</b>ため列車Assigned 経路が別物になって点滅 /
     * 誤経路发生过。クライアント側は {@code RenderRailsMixin} が毎フレーム map を更新している
     * ので、map から復元すれば常に最新かつ整合した値を得られる。
     *
     * <p>MTR の {@code SidingPathFinder} は {@code Angle} を <b>参照一致</b>で比較する
     * （{@code if_acmpeq}）。角度は {@link AngleExtra#canonicalize} で 0.001 度単位に丸めてから
     * キャッシュするので、同じ向きは必ず同一インスタンスが返り、参照一致が成立する。
     */
    @Unique
    private void bte$restoreOverrideFromMap() {
        final java.util.Map<String, double[]> map = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP;
        if (map.isEmpty()) return; // BTE が一切使われていないワールドでは何もしない

        final Position p1 = getPosition1();
        final Position p2 = getPosition2();
        if (p1 == null || p2 == null) return;

        // d[10..13] = publish 元ノードの端点座標で向きを判定し、要求順へ読み替える。
        final double[] rad = new double[2];
        if (!StraightNodeBlockEntity.getCachedExitAngles(
                StraightNodeBlockEntity.railMathKey(p1, p2),
                Init.positionToBlockPos(p1),
                Init.positionToBlockPos(p2),
                rad)) return;

        this.bte$angleOverride1 = AngleExtra.fromDegrees(Math.toDegrees(rad[0]));
        this.bte$angleOverride2 = AngleExtra.fromDegrees(Math.toDegrees(rad[1]));
    }

    @Inject(method = "getStartAngle(Lorg/mtr/core/data/Position;)Lorg/mtr/core/tool/Angle;", at = @At("HEAD"), cancellable = true)
    private void bte$getStartAngleByPosition(Position startPosition, CallbackInfoReturnable<Angle> cir) {
        if (bte$angleOverride1 == null && bte$angleOverride2 == null) {
            bte$restoreOverrideFromMap();
            if (bte$angleOverride1 == null && bte$angleOverride2 == null) return;
        }
        if (startPosition.equals(getPosition1())) {
            if (bte$angleOverride1 != null) cir.setReturnValue(bte$angleOverride1);
        } else if (startPosition.equals(getPosition2())) {
            if (bte$angleOverride2 != null) cir.setReturnValue(bte$angleOverride2);
        }
    }

    @Inject(method = "getStartAngle(Z)Lorg/mtr/core/tool/Angle;", at = @At("HEAD"), cancellable = true)
    private void bte$getStartAngleByReversed(boolean reversed, CallbackInfoReturnable<Angle> cir) {
        if (bte$angleOverride1 == null && bte$angleOverride2 == null) {
            bte$restoreOverrideFromMap();
            if (bte$angleOverride1 == null && bte$angleOverride2 == null) return;
        }
        final boolean reversePositions = getPosition1().compareTo(getPosition2()) > 0;
        final Angle override = (reversePositions == reversed) ? bte$angleOverride1 : bte$angleOverride2;
        if (override != null) cir.setReturnValue(override);
    }
}
