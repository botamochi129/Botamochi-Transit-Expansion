package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Rail.class, remap = false)
public abstract class RailMixin {

    // 【核心】Rail.getAngles が呼ばれた瞬間をフックし、自由角度を返す
    @Inject(method = "getAngles", at = @At("HEAD"), cancellable = true)
    private static void bte$getAngles(Position startPosition, float startAngle, Position endPosition, float endAngle, CallbackInfoReturnable<ObjectObjectImmutablePair<Angle, Angle>> cir) {
        // 端点座標からキーを生成
        long x1 = Math.min(startPosition.getX(), endPosition.getX());
        long y1 = Math.min(startPosition.getY(), endPosition.getY());
        long z1 = Math.min(startPosition.getZ(), endPosition.getZ());
        long x2 = Math.max(startPosition.getX(), endPosition.getX());
        long y2 = Math.max(startPosition.getY(), endPosition.getY());
        long z2 = Math.max(startPosition.getZ(), endPosition.getZ());
        String key = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;

        // 静的マップからデータを取得
        double[] bezierData = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(key);
        if (bezierData != null) {
            // マップにデータがあれば、自由角度を返す
            Angle newStartAngle = Angle.fromAngle((float) Math.toDegrees(bezierData[6]));
            Angle newEndAngle = Angle.fromAngle((float) Math.toDegrees(bezierData[7]));
            cir.setReturnValue(new ObjectObjectImmutablePair<>(newStartAngle, newEndAngle));
        }
    }
}