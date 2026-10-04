package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.AngleExtra;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.Init;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@link Rail#copy} が内部で呼び出す {@code Rail} コンストラクタを横取りし、
 * BTE ノードの軸角から求め直した角度でコンストラクタを実行する。
 *
 * <p><b>なぜ {@code @Redirect(NEW)} を使うか</b><br>
 * 1. {@code @ModifyArgs} は Forge 環境で他 MOD のクラスに対して使用すると、
 *    合成クラス ({@code Args$1}) の ClassLoader 解決に失敗し {@code NoClassDefFoundError} となる。
 * 2. {@code @ModifyReturnValue} で {@code Rail.newRail} を使うと、MTR API の仕様上
 *    {@code canTurnBack} などの情報が欠落したり、型変換エラーが発生する。
 * 3. {@code @Redirect} で {@code NEW} をターゲットにすると、コンストラクタの全引数が
 *    直接渡ってくるため、protected メソッドへのアクセスや型変換の問題が一切発生せず、
 *    private コンストラクタの呼び出しも Mixin が安全に置き換える。
 */
@Mixin(value = Rail.class, remap = false)
public abstract class RailCopyAnglesMixin {

    /**
     * {@code copy(Rail, Shape, double)} 内のコンストラクタ呼び出しを横取りする。
     */
    @Redirect(
            method = "copy(Lorg/mtr/core/data/Rail;Lorg/mtr/core/data/Rail$Shape;D)Lorg/mtr/core/data/Rail;",
            at = @At(value = "NEW", target = "Lorg/mtr/core/data/Rail;")
    )
    private static Rail bte$redirectNewRailShape(
            Position position1, Angle angle1, Position position2, Angle angle2,
            Rail.Shape shape, double verticalRadius, ObjectArrayList<String> styles,
            long speedLimit1, long speedLimit2, boolean isPlatform, boolean isSiding,
            boolean canAccelerate, boolean canTurnBack, boolean canConnectRemotely,
            boolean canHaveSignal, TransportMode transportMode
    ) {
        // BTEノードなら角度を計算し直し、MTR標準ノードなら元の角度をそのまま使う
        angle1 = bte$resolveAngle(position1, angle1, position2);
        angle2 = bte$resolveAngle(position2, angle2, position1);

        // 書き換えた角度でコンストラクタを呼び出す
        return Rail.newRail(position1, angle1, position2, angle2, shape, verticalRadius, styles,
                speedLimit1, speedLimit2, isPlatform, isSiding, canAccelerate, canTurnBack,
                canConnectRemotely, transportMode);
    }

    /**
     * {@code copy(Rail, ObjectArrayList)} 内のコンストラクタ呼び出しを横取りする。
     */
    @Redirect(
            method = "copy(Lorg/mtr/core/data/Rail;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;)Lorg/mtr/core/data/Rail;",
            at = @At(value = "NEW", target = "Lorg/mtr/core/data/Rail;")
    )
    private static Rail bte$redirectNewRailStyles(
            Position position1, Angle angle1, Position position2, Angle angle2,
            Rail.Shape shape, double verticalRadius, ObjectArrayList<String> styles,
            long speedLimit1, long speedLimit2, boolean isPlatform, boolean isSiding,
            boolean canAccelerate, boolean canTurnBack, boolean canConnectRemotely,
            boolean canHaveSignal, TransportMode transportMode
    ) {
        angle1 = bte$resolveAngle(position1, angle1, position2);
        angle2 = bte$resolveAngle(position2, angle2, position1);

        return Rail.newRail(position1, angle1, position2, angle2, shape, verticalRadius, styles,
                speedLimit1, speedLimit2, isPlatform, isSiding, canAccelerate, canTurnBack,
                canConnectRemotely, transportMode);
    }

    /**
     * 端点の角度を解決する。
     * BTEノードでなければ、MTR本家の元の角度をそのまま返す。
     */
    @Unique
    private static Angle bte$resolveAngle(Position selfPos, Angle originalAngle, Position otherPos) {
        final Float axis = bte$resolveNodeAxis(selfPos);
        if (axis == null) {
            return originalAngle;
        }

        final float geo = (float) Math.toDegrees(Math.atan2(
                otherPos.getZ() - selfPos.getZ(),
                otherPos.getX() - selfPos.getX()
        ));

        return AngleExtra.fromDegrees(axis + (Angle.similarFacing(axis, geo) ? 0f : 180f));
    }

    /**
     * この端点が bound な BTE ノードで offset が全てゼロなら、その生軸を返す。
     */
    @Unique
    private static Float bte$resolveNodeAxis(Position position) {
        final BlockPos blockPos = Init.positionToBlockPos(position);
        final double[] out = new double[4];
        if (!StraightNodeBlockEntity.getNativeNodeAxis(blockPos, out)) return null;
        return (float) out[0];
    }
}