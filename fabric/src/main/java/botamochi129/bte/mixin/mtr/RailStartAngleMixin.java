package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.AngleExtra;
import botamochi129.bte.mod.data.RailAngleOverride;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
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

    @Inject(method = "getStartAngle(Lorg/mtr/core/data/Position;)Lorg/mtr/core/tool/Angle;", at = @At("HEAD"), cancellable = true)
    private void bte$getStartAngleByPosition(Position startPosition, CallbackInfoReturnable<Angle> cir) {
        if (bte$angleOverride1 == null && bte$angleOverride2 == null) return;
        if (startPosition.equals(getPosition1())) {
            if (bte$angleOverride1 != null) cir.setReturnValue(bte$angleOverride1);
        } else if (startPosition.equals(getPosition2())) {
            if (bte$angleOverride2 != null) cir.setReturnValue(bte$angleOverride2);
        }
    }

    @Inject(method = "getStartAngle(Z)Lorg/mtr/core/tool/Angle;", at = @At("HEAD"), cancellable = true)
    private void bte$getStartAngleByReversed(boolean reversed, CallbackInfoReturnable<Angle> cir) {
        if (bte$angleOverride1 == null && bte$angleOverride2 == null) return;
        final boolean reversePositions = getPosition1().compareTo(getPosition2()) > 0;
        final Angle override = (reversePositions == reversed) ? bte$angleOverride1 : bte$angleOverride2;
        if (override != null) cir.setReturnValue(override);
    }
}
