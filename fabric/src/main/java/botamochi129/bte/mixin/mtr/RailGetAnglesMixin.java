package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.Init;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link Rail#getAngles} に対し、BTE ノード側の生軸を差し替えて
 * MTR に {@code RailMath} をネイティブに構築させる。
 *
 * <p><b>MTR 本家のアルゴリズム（{@code Rail.getAngles} の全容）</b><br>
 * バイトコードで検証した本家の式：
 * <pre>
 * float geo = (float) Math.toDegrees(Math.atan2(p2.getZ()-p1.getZ(), p2.getX()-p1.getX()));
 * return new ObjectObjectImmutablePair&lt;&gt;(
 *     Angle.fromAngle(a1 + (Angle.similarFacing(a1, geo) ?   0 : 180)),
 *     Angle.fromAngle(a2 + (Angle.similarFacing(a2, geo) ? 180 :   0))
 * );
 * </pre>
 *
 * <p><b>両端は完全に独立している</b><br>
 * 上式を逆アセンブルすると、返り値の前半は {@code a1} と {@code geo} のみを、
 * 後半は {@code a2} と {@code geo} のみを参照している（相互参照は無い）。
 * つまり {@code a1} を BTE の軸に差し替えても {@code a2} の計算結果には影響せず、
 * 逆も同様である。したがって<b>端ごとに独立して差し替えられる</b>。
 *
 * <p><b>BTE の旧実装の問題</b><br>
 * BTE は同じ計算を {@code ArcCurve}/{@code RailCalculator} で再実装し、
 * できあがった値を<b>構築済み</b> {@code RailMath} の final へ後から書き戻していた。
 * しかし MTR の不変条件は「final は {@code <init>} で一度だけ焼き込み、以降は書き換えない。
 * 形状を変えたい場合は {@code Rail} を作り直す」であり、<b>BTE はこれを破っていた</b>。
 * 結果、{@code RailMath} の final から導出される
 * {@code MinecraftClientData$RailWrapper} の遮蔽カリング AABB、{@code Rail#closeTo}、
 * 接続キャッシュ、経路探索がすべて「構築時の値」のまま中途半端に古くなり、
 * どのキャッシュが先に作り直されるかがロード状況（= 描画距離）で変わっていた。
 *
 * <p><b>差し替えの条件</b><br>
 * <ul>
 *   <li>その端点が bound な BTE ノードで、<b>かつサブブロック offset が全てゼロ</b>であること。
 *       offset は {@code RailMath} の入力（{@code Position} 整数と {@code Angle} 2 つ）に
 *       表現できないため、そのような端は MTR 本家の値に任せる。</li>
 *   <li>BTE 端は差し替え、<b>MTR 標準ノードの端は本家の引数をそのまま通す</b>。
 *       これが BTE 1端 + MTR 1端の混在レールをネイティブに扱える理由であり、
 *       BTE の存在意義（任意角ノードを既存線路に繋ぐ）そのものである。</li>
 * </ul>
 */
@Mixin(value = Rail.class, remap = false)
public abstract class RailGetAnglesMixin {

    /**
     * BTE ノードの端だけを差し替え、MTR 本家と同じ式で {@code getAngles} の結果を決定する。
     *
     * <p>両端とも差し替え不要（＝どちらの端も BTE ノードでない、または offset が非ゼロ）の場合は
     * {@code cir} を返さないので、MTR 本家の処理がそのまま走る。
     */
    @Inject(
            method = "getAngles(Lorg/mtr/core/data/Position;FLorg/mtr/core/data/Position;F)Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectObjectImmutablePair;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void bte$useBteNodeAngles(
            Position position1, float angle1, Position position2, float angle2,
            CallbackInfoReturnable<ObjectObjectImmutablePair<Angle, Angle>> cir
    ) {
        if (position1 == null || position2 == null) return;

        // 端ごとに独立して差し替える。MTR 標準ノードの端は本家の値をそのまま使う。
        final Float bteAxis1 = bte$resolveNodeAxis(position1);
        final Float bteAxis2 = bte$resolveNodeAxis(position2);
        if (bteAxis1 == null && bteAxis2 == null) return;

        final float axis1 = (bteAxis1 != null) ? bteAxis1 : angle1;
        final float axis2 = (bteAxis2 != null) ? bteAxis2 : angle2;

        // ここから先は MTR 本家と同じ式（バイトコードで検証済み）。
        // 本家が使うのは生のノード軸なので、差し替え後の値をそのまま渡す。
        final float geo = (float) Math.toDegrees(Math.atan2(
                position2.getZ() - position1.getZ(),
                position2.getX() - position1.getX()
        ));

        cir.setReturnValue(new ObjectObjectImmutablePair<>(
                Angle.fromAngle(axis1 + (Angle.similarFacing(axis1, geo) ? 0f : 180f)),
                Angle.fromAngle(axis2 + (Angle.similarFacing(axis2, geo) ? 180f : 0f))
        ));

        // BTE 端を1つでも差し替えたので、この端点対は MTR 自身が正しく構築する。
        // in-place 注入を無効化する。差し込んだ軸そのものを記録しておき、
        // 後からノード軸や offset が変わった時点で isNativeRail が false を返すようにする
        // （恒久フラグのままだと、形状が更新されず再入場まで直らない）。
        StraightNodeBlockEntity.markNativeRail(
                StraightNodeBlockEntity.railMathKey(position1, position2),
                position1, position2,
                bteAxis1, bteAxis2
        );
    }

    /**
     * この端点が bound な BTE ノードで offset が全てゼロなら、その生軸を返す。
     * それ以外は {@code null}（＝MTR 本家の値をそのまま使う）。
     */
    @Unique
    private static Float bte$resolveNodeAxis(Position position) {
        if (position == null) return null;

        final BlockPos blockPos = Init.positionToBlockPos(position);
        final double[] out = new double[4];
        if (!StraightNodeBlockEntity.getNativeNodeAxis(blockPos, out)) return null;
        return (float) out[0];
    }
}