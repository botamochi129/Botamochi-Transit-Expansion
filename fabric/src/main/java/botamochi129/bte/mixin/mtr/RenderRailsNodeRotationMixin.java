package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.model.ModelSmallCube;
import org.mtr.mod.render.RenderRails;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(value = RenderRails.class, remap = false)
public abstract class RenderRailsNodeRotationMixin {

    // RenderRails クラス内の private static フィールドにアクセスするための Shadow
    @Shadow
    private static ModelSmallCube MODEL_SMALL_CUBE;

    /**
     * renderNode メソッドの先頭でフックし、StraightNodeBlockEntity の場合に独自の描画処理を実行する
     */
    @Inject(method = "renderNode", at = @At("HEAD"), cancellable = true)
    private static void bte$renderCustomNode(
            BlockState blockState,
            BlockPos blockPos,
            BooleanSupplier shouldRender,
            int light,
            CallbackInfo ci
    ) {
        // 元の条件式を踏襲
        if (blockState.getBlock().data instanceof org.mtr.mod.block.BlockNode && shouldRender.getAsBoolean()) {
            ClientWorld world = MinecraftClient.getInstance().getWorldMapped();

            if (world != null) {
                BlockEntity be = world.getBlockEntity(blockPos);

                // StraightNodeBlockEntity かつ バインド済みの場合
                if (be != null && be.data instanceof StraightNodeBlockEntity nodeBe && nodeBe.isBound()) {

                    // 独自の描画変換行列を構築
                    final StoredMatrixTransformations storedMatrixTransformations = new StoredMatrixTransformations(
                            blockPos.getX() + 0.5,
                            blockPos.getY(),
                            blockPos.getZ() + 0.5
                    );

                    storedMatrixTransformations.add(graphicsHolder -> {
                        // 【ここが核心】自由角度 (double) で回転させる
                        graphicsHolder.rotateYDegrees((float) nodeBe.getAngleDegrees());
                        graphicsHolder.scale(4, 0.5F, 0.5F);
                        graphicsHolder.translate(-0.5, 0, -0.5);
                    });

                    // Shadow で取得したモデルを描画
                    MODEL_SMALL_CUBE.render(storedMatrixTransformations, light);

                    // 元の MTR 標準の描画処理 (22.5度スナップ処理) をキャンセル
                    ci.cancel();
                }
            }
        }
    }
}