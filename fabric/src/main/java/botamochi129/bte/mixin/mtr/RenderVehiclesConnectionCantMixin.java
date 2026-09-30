package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.rail.RailCantDirectionTracker;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 車両間連結部（gangway / barrier）に、走行中のレールのカント角をロールとして与える。
 *
 * <p>{@link RenderVehiclesCantMixin} の {@code getStoredMatrixTransformations} は
 * {@code renderConnection} を通過しないため、連結部は本 Mixin で別途扱う。
 *
 * <h2>{@code renderConnection} 内部への注入</h2>
 * MTR 4.0.5 の {@code renderConnection} は末尾の {@code double}（引数 index 17）を
 * 冒頭で {@code local28 = -Math.toRadians(d17)} と一度だけ計算し、その {@code local28} を
 * gangway / barrier の 8 箇所すべてで {@code Vector.rotateZ} に渡している。
 * よって {@code Math.toRadians} 1 箇所を {@link Redirect} すれば連結部の Z 回転全体に
 * 合成でき、呼び出し側 {@code lambda$render$12} の 2 箇所を個別に扱う必要がない。
 *
 * <h2>なぜ {@code @ModifyArgs} を使わないか</h2>
 * {@code @ModifyArgs} は Mixin が {@code org.spongepowered.asm.synthetic.args.Args$N} を
 * 実行時に生成するが、ModLauncher（Forge）では対象が他 MOD の JAR 内クラスである場合にその
 * 合成クラスを classloader に定義できず、実ワールド描画で
 * {@code NoClassDefFoundError: org/spongepowered/asm/synthetic/args/Args$1} となる。
 * {@link Inject} と {@link Redirect} は合成クラスを生成しないため両 loader で安全。
 *
 * <p>位置は {@code renderConnection} の第 11 引数 {@code PositionAndRotation} から取得する。
 * {@link Redirect} の handler にはリダイレクト対象（{@code Math.toRadians}）の引数しか
 * 渡らないため、{@link Inject} で {@code PositionAndRotation} を受け取ってカントを
 * {@link #bte$connectionRoll} に退避しておく。
 * {@link Inject} の handler は対象メソッドの引数プレフィックスではなく
 * <b>全 19 引数</b>（末尾 {@code boolean} まで）＋ {@link CallbackInfo} を要求する。
 * ここを省略すると {@code InvalidInjectionException: Invalid descriptor on @Inject} で
 * {@code RenderVehicles} の変換自体が失敗する。
 * {@link RailCantDirectionTracker#cantFor} は world 未構築時も 0 を返す実装なので
 * {@code @At("HEAD")} で構わない。
 *
 * <p>{@code Math.toRadians} は {@code renderConnection} 内に 1 箇所しか無いため、
 * {@code require = 1} により MTR 側の変更を起動時に検出できる。
 */
@Mixin(value = RenderVehicles.class, remap = false)
public abstract class RenderVehiclesConnectionCantMixin {

    @Unique
    private static final String TO_RADIANS = "Ljava/lang/Math;toRadians(D)D";

    /**
     * {@code renderConnection} の {@link #bte$applyConnectionRoll} へ渡す退避カント。
     * 描画はクライアントスレッドの逐次処理で {@code renderConnection} は再入しないため、
     * フィールド単一でスレッド安全。
     */
    @Unique
    private static double bte$connectionRoll;

    @Inject(method = "renderConnection", at = @At("HEAD"), require = 1)
    private static void bte$captureConnectionRoll(
            final boolean flag1,
            final boolean flag2,
            final boolean flag3,
            final RenderVehicles.PreviousConnectionPositions previousConnectionPositions,
            final Identifier identifier1,
            final Identifier identifier2,
            final Identifier identifier3,
            final Identifier identifier4,
            final Identifier identifier5,
            final Identifier identifier6,
            final PositionAndRotation positionAndRotation,
            final boolean flag4,
            final double value1,
            final double value2,
            final double value3,
            final double value4,
            final double value5,
            final double value6,
            final boolean flag5,
            final CallbackInfo ci
    ) {
        bte$connectionRoll = bte$computeRoll(positionAndRotation);
    }

    @Redirect(
            method = "renderConnection",
            at = @At(value = "INVOKE", target = TO_RADIANS, ordinal = 0),
            require = 1
    )
    private static double bte$applyConnectionRoll(final double radians) {
        final double roll = bte$connectionRoll;
        if (roll == 0.0D) {
            return radians;
        }
        // MTR は -(toRadians(d17)) を rotateZ に渡す。回転角を +roll 増やすには引数 d17 を
        // -roll すればよく、toRadians(D) は線形なので戻り値から toRadians(roll) を引けば
        // d17 を書き換えたのと同じ結果になる。
        return radians - Math.toRadians(roll);
    }

    @Unique
    private static double bte$computeRoll(final PositionAndRotation positionAndRotation) {
        if (positionAndRotation == null) {
            return 0.0D;
        }
        final Vector position = positionAndRotation.position;
        if (position == null) {
            return 0.0D;
        }
        // 車両本体と同じ tracker / 同じ stream を使い、連結部と車体の符号を一致させる。
        return RailCantDirectionTracker.cantFor(
                RailCantDirectionTracker.STREAM_BODY,
                position.x(), position.y(), position.z()
        ).cant;
    }
}
