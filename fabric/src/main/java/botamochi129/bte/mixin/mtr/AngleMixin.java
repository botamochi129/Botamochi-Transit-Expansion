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
        final float deg = (float) degrees;
        for (Angle a : Angle.values()) {
            if (a.angleDegrees == deg) return a;
        }
        Angle cached = AngleExtra.BTE$PHANTOM_CACHE.get(deg);
        if (cached != null) return cached;
        Angle result = bte$create("D" + deg, -1, deg);
        ((AngleExtra) (Object) result).bte$setRadians(Math.toRadians(degrees));
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
}