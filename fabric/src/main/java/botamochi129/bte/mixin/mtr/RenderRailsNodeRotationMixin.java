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

                    // ★ 追加: オフセット値の取得
                    double offX = nodeBe.getOffsetX();
                    double offY = nodeBe.getOffsetY();
                    double offZ = nodeBe.getOffsetZ();

                    // ★ 修正: オフセット値を基準座標に加算する
                    final StoredMatrixTransformations storedMatrixTransformations = new StoredMatrixTransformations(
                            blockPos.getX() + 0.5 + offX,
                            blockPos.getY() + offY,
                            blockPos.getZ() + 0.5 + offZ
                    );

                    storedMatrixTransformations.add(graphicsHolder -> {
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