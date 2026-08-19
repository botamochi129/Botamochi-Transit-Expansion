package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.CantContext;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

@Mixin(value = MainRenderer.class, remap = false)
public abstract class MainRendererMixin {

    @Unique
    private static final ThreadLocal<Boolean> IS_WRAPPING = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "scheduleRender(Lorg/mtr/mapping/holder/Identifier;ZLorg/mtr/mod/render/QueuedRenderLayer;Ljava/util/function/BiConsumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void bte$wrapRenderTask(
            Identifier texture, boolean translucent, QueuedRenderLayer layer,
            BiConsumer<GraphicsHolder, Vector3d> renderAction, CallbackInfo ci
    ) {
        if (IS_WRAPPING.get()) return;

        // ★ 修正: TEXT (レールタイプ表示) や LINES, LIGHT は回転させない
        // これらを回転させると、テキストが見切れたり、MTRのUIが壊れる
        if (layer == QueuedRenderLayer.TEXT || layer == QueuedRenderLayer.LINES || layer == QueuedRenderLayer.LIGHT) {
            return;
        }

        CantContext.CantData cantData = CantContext.get();
        if (cantData != null && Math.abs(cantData.rollRad) > 0.001) {
            ci.cancel();

            final double roll = cantData.rollRad;
            final double cx = cantData.centerX;
            final double cy = cantData.centerY;
            final double cz = cantData.centerZ;
            final double yaw = Math.atan2(cantData.dirZ, cantData.dirX);

            IS_WRAPPING.set(true);
            try {
                BiConsumer<GraphicsHolder, Vector3d> wrappedAction = (graphicsHolder, offset) -> {
                    graphicsHolder.push();

                    double rcx = cx - offset.getXMapped();
                    double rcy = cy - offset.getYMapped();
                    double rcz = cz - offset.getZMapped();

                    graphicsHolder.translate(rcx, rcy, rcz);
                    graphicsHolder.rotateYRadians((float) -yaw);
                    graphicsHolder.rotateXRadians((float) -roll);
                    graphicsHolder.rotateYRadians((float) yaw);
                    graphicsHolder.translate(-rcx, -rcy, -rcz);

                    renderAction.accept(graphicsHolder, offset);

                    graphicsHolder.pop();
                };
                MainRenderer.scheduleRender(texture, translucent, layer, wrappedAction);
            } finally {
                IS_WRAPPING.set(false);
            }
        }
    }
}