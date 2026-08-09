package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.tool.Angle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PathData.class, remap = false)
public abstract class PathDataMixin {

    // 【修正】PathData に明確に定義されているメソッドのみを Shadow する
    @Shadow public abstract Position getOrderedPosition1();
    @Shadow public abstract Position getOrderedPosition2();

    // 【核心】writePathCache が呼ばれたタイミングで、親クラスのフィールドをリフレクションで書き換える
    @Inject(method = "writePathCache(Lorg/mtr/core/data/Data;)V", at = @At("RETURN"))
    private void bte$writePathCache(org.mtr.core.data.Data data, CallbackInfo ci) {
        Position pos1 = getOrderedPosition1();
        Position pos2 = getOrderedPosition2();
        if (pos1 == null || pos2 == null) return;

        // 端点座標からキーを生成
        long x1 = Math.min(pos1.getX(), pos2.getX());
        long y1 = Math.min(pos1.getY(), pos2.getY());
        long z1 = Math.min(pos1.getZ(), pos2.getZ());
        long x2 = Math.max(pos1.getX(), pos2.getX());
        long y2 = Math.max(pos1.getY(), pos2.getY());
        long z2 = Math.max(pos1.getZ(), pos2.getZ());
        String railMathKey = x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2;

        double[] bezierData = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(railMathKey);
        if (bezierData != null) {
            Angle newStartAngle = Angle.fromAngle((float) Math.toDegrees(bezierData[6]));
            Angle newEndAngle = Angle.fromAngle((float) Math.toDegrees(bezierData[7]));

            // 【超重要】リフレクションを使って、親クラス (PathDataSchema) の startAngle と endAngle を直接書き換える
            try {
                Class<?> superClass = this.getClass().getSuperclass();

                java.lang.reflect.Field startAngleField = superClass.getDeclaredField("startAngle");
                startAngleField.setAccessible(true);
                startAngleField.set(this, newStartAngle);

                java.lang.reflect.Field endAngleField = superClass.getDeclaredField("endAngle");
                endAngleField.setAccessible(true);
                endAngleField.set(this, newEndAngle);

            } catch (Exception e) {
                System.err.println("[BTE] Failed to update PathData angles via reflection: " + e.getMessage());
            }
        }
    }
}