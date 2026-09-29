package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.AngleExtra;
import org.mtr.core.tool.Angle;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = Angle.class, remap = false)
public abstract class AngleMixin implements AngleExtra {

    @Shadow(remap = false) @Final public float angleDegrees;
    @Shadow(remap = false) @Final @Mutable public double angleRadians, sin, cos, tan, halfTan;

    @Invoker(value = "<init>")
    private static Angle bte$create(String name, int ordinal, float angleDegrees) {
        throw new IllegalStateException();
    }

    private boolean bte$isPhantom() {
        return ((Angle) (Object) this).ordinal() < 0;
    }

    private static float bte$normalize(float deg) {
        deg %= 360f;
        if (deg < 0) deg += 360f;
        return deg;
    }

    @Override
    public Angle bte$fromDegrees(double degrees) {
        // ★ 修正: 正規化してから比較/キャッシュする。
        // MTR の Angle.angleDegrees は Angle.java の normalizeAngle で [-180, 180) に
        // 正規化されているが、以前のコードは比較もキャッシュキーも正規化前の
        // 入力角 ([-180, 180) ではない値) を使っていたため:
        //   - 225.0 が NW(-135.0) に一致せず、同じ向きの phantom が作られていた
        //   - 200.0 と -160.0 が別キャッシュエントリ(=別インスタンス)になっていた
        // 結果として SidingPathFinder.getConnections の `node.angle == rail.getStartAngle(pos)`
        // が false になり、自由角度のノードを含む区間でパスが繋がらなかった。
        final float deg = AngleExtra.canonicalize((float) degrees);

        for (Angle a : Angle.values()) {
            if (a.angleDegrees == deg) return a;
        }

        Angle cached = AngleExtra.BTE$PHANTOM_CACHE.get(deg);
        if (cached != null) return cached;

        // 名前には正規化済みの値を入れる。シリアライズ (RailSchema/PathDataSchema の
        // angle.toString()) → 復号 (EnumHelperMixin) の往復で同一キャッシュキーに戻る。
        Angle result = bte$create("D" + deg, -1, deg);
        ((AngleExtra) (Object) result).bte$setRadians(Math.toRadians(deg));
        AngleExtra.BTE$PHANTOM_CACHE.put(deg, result);
        return result;
    }

    @Override
    public void bte$setRadians(double rad) {
        this.angleRadians = rad;
        this.sin = Math.sin(rad);
        this.cos = Math.cos(rad);
        this.tan = Math.tan(rad);
        this.halfTan = Math.tan(rad / 2);
    }

    @Inject(method = "getOpposite", at = @At("HEAD"), remap = false, cancellable = true)
    private void bte$getOpposite(CallbackInfoReturnable<Angle> cir) {
        if (bte$isPhantom()) cir.setReturnValue(AngleExtra.fromDegrees(angleDegrees + 180));
    }

    @Inject(method = "isParallel", at = @At("HEAD"), remap = false, cancellable = true)
    private void bte$isParallel(Angle angle, CallbackInfoReturnable<Boolean> cir) {
        if (bte$isPhantom() || angle.ordinal() < 0) {
            float diff = Math.abs(bte$normalize(angleDegrees - angle.angleDegrees));
            cir.setReturnValue(diff < 0.001f || Math.abs(diff - 180f) < 0.001f);
        }
    }

    @Inject(method = "add", at = @At("HEAD"), remap = false, cancellable = true)
    private void bte$add(Angle angle, CallbackInfoReturnable<Angle> cir) {
        if (bte$isPhantom() || angle.ordinal() < 0)
            cir.setReturnValue(AngleExtra.fromDegrees(angleDegrees + angle.angleDegrees));
    }

    @Inject(method = "sub", at = @At("HEAD"), remap = false, cancellable = true)
    private void bte$sub(Angle angle, CallbackInfoReturnable<Angle> cir) {
        if (bte$isPhantom() || angle.ordinal() < 0)
            cir.setReturnValue(AngleExtra.fromDegrees(angleDegrees - angle.angleDegrees));
    }

    // MTR の getClosest45 は switch(this) なので phantom (ordinal < 0) では default に
    // 落ちて自分自身を返す。Platform.getOBAStopDetails がこれを StopDirection の値として
    // 使うため "D45.3" が GTFS 出力に漏れるので、45 度刻みに丸めて返す。
    // MTR 本体は 22.5 度刻みを 8 方位 (E/SE/S/SW/W/NW/N/NE) へ丸めるので、
    // ここに phantom が来ることはない = 22.5 度丸めは正しい。
    @Inject(method = "getClosest45", at = @At("HEAD"), remap = false, cancellable = true)
    private void bte$getClosest45(CallbackInfoReturnable<Angle> cir) {
        if (bte$isPhantom()) cir.setReturnValue(Angle.fromAngle(Math.round(angleDegrees / 45.0f) * 45.0f));
    }
}