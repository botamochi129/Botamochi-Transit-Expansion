package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.RailAccessor;
import org.mtr.core.data.Position;
import org.mtr.core.generated.data.RailSchema;
import org.mtr.core.tool.Angle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * {@link RailAccessor} の実装。
 * <p>
 * 対象は {@code Rail} ではなくその基底クラス {@code RailSchema} である。
 * 端点座標・端点角度・制限速度などの保存値は {@code RailSchema} に
 * {@code protected final} で宣言されており、Mixin は
 * 「対象クラス自身に宣言された」メンバしか {@code @Shadow} できないためである。
 * <p>
 * {@code RailSchema} にこの Mixin を適用すれば、{@code Rail extends RailSchema} なので
 * {@code Rail} インスタンスが {@link RailAccessor} を実装する事实になり、
 * {@code Rail} 自身に Mixin を追加せずに保存値へ 접근できる。
 * <p>
 * ここ経由で読むことで、
 * {@link RailStartAngleMixin} が {@code getStartAngle()} に与える
 * BTE 由来の一時的な角度を避け、MTR の保存値をそのまま扱える。
 */
@Mixin(value = RailSchema.class, remap = false)
public abstract class RailAccessorMixin implements RailAccessor {

    @Shadow
    private Position position1;

    @Shadow
    private Position position2;

    @Shadow
    private Angle angle1;

    @Shadow
    private Angle angle2;

    @Shadow
    private long speedLimit1;

    @Shadow
    private long speedLimit2;

    @Shadow
    private boolean canHaveSignal;

    @Override
    public Position bte$getPosition1() {
        return position1;
    }

    @Override
    public Position bte$getPosition2() {
        return position2;
    }

    @Override
    public Angle bte$getSavedAngle1() {
        return angle1;
    }

    @Override
    public Angle bte$getSavedAngle2() {
        return angle2;
    }

    @Override
    public long bte$getSpeedLimit1Kmh() {
        return speedLimit1;
    }

    @Override
    public long bte$getSpeedLimit2Kmh() {
        return speedLimit2;
    }

    @Override
    public boolean bte$canHaveSignal() {
        return canHaveSignal;
    }
}
