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

    @Shadow
    private static ModelSmallCube MODEL_SMALL_CUBE;

    @Inject(method = "renderNode", at = @At("HEAD"), cancellable = true)
    private static void bte$renderCustomNode(
            BlockState blockState,
            BlockPos blockPos,
            BooleanSupplier shouldRender,
            int light,
            CallbackInfo ci
    ) {
        if (blockState.getBlock().data instanceof org.mtr.mod.block.BlockNode && shouldRender.getAsBoolean()) {
            ClientWorld world = MinecraftClient.getInstance().getWorldMapped();

            if (world != null) {
                BlockEntity be = world.getBlockEntity(blockPos);

                if (be != null && be.data instanceof StraightNodeBlockEntity nodeBe && nodeBe.isBound()) {

                    final StoredMatrixTransformations storedMatrixTransformations = new StoredMatrixTransformations(
                            blockPos.getX() + 0.5,
                            blockPos.getY(),
                            blockPos.getZ() + 0.5
                    );

                    storedMatrixTransformations.add(graphicsHolder -> {
                        // 【最終修正】スライダーを右に動かしたとき、モデルも右回り（時計回り）に回転するようにする
                        // MTRノードモデルのデフォルトは「北」を向いている。
                        // 東(0°)を向かせるには、右回りに90°回転させる必要がある。
                        // Minecraftの rotateYDegrees は正の値で「左回り」なので、右回りに90°回転させるには -90.0F を指定する。
                        float renderAngle = -(float) nodeBe.getAngleDegrees() - 90.0F;

                        graphicsHolder.rotateYDegrees(renderAngle);
                        graphicsHolder.scale(4, 0.5F, 0.5F);
                        graphicsHolder.translate(-0.5, 0, -0.5);
                    });

                    MODEL_SMALL_CUBE.render(storedMatrixTransformations, light);
                    ci.cancel();
                }
            }
        }
    }
}