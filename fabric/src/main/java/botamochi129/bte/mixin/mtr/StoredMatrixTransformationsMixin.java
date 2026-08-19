package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.CantContext;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StoredMatrixTransformations.class, remap = false)
public abstract class StoredMatrixTransformationsMixin {

    @Inject(method = "transform", at = @At("RETURN"))
    private void bte$applyCantOnTransform(GraphicsHolder graphicsHolder, Vector3d offset, CallbackInfo ci) {
        CantContext.CantData cantData = CantContext.get();
        if (cantData != null && Math.abs(cantData.rollRad) > 0.001) {
            // ★ 修正: offset (カメラ位置) を考慮したレール中心座標を計算
            // これにより、カメラが遠くにあっても回転中心がズレず、モデルが飛ばない
            double rcx = cantData.centerX - offset.getXMapped();
            double rcy = cantData.centerY - offset.getYMapped();
            double rcz = cantData.centerZ - offset.getZMapped();

            // レール中心を原点に移動
            graphicsHolder.translate(rcx, rcy, rcz);

            // ★ Z軸回転（ロール）のみを追加
            // MTRは既にモデルをレールの向き(yaw)に回転させているため、
            // ここでZ軸回転させれば、それがそのまま「進行方向に対するバンク」になります。
            // yawの計算やrotateYは不要（むしろMTRの回転をキャンセルしてしまうため有害）です。
            graphicsHolder.rotateZRadians((float) cantData.rollRad);

            // 元に戻す
            graphicsHolder.translate(-rcx, -rcy, -rcz);
        }
    }
}