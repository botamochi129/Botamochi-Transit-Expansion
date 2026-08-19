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

                    double offX = nodeBe.getOffsetX();
                    double offY = nodeBe.getOffsetY();
                    double offZ = nodeBe.getOffsetZ();

                    final StoredMatrixTransformations storedMatrixTransformations = new StoredMatrixTransformations(
                            blockPos.getX() + 0.5 + offX,
                            blockPos.getY() + offY,
                            blockPos.getZ() + 0.5 + offZ
                    );

                    storedMatrixTransformations.add(graphicsHolder -> {
                        float renderAngle = -(float) nodeBe.getAngleDegrees() - 90.0F;
                        float rollAngle = (float) nodeBe.getRollDegrees(); // ★ 追加

                        graphicsHolder.rotateYDegrees(renderAngle);
                        // ★ 修正: Z軸回転（ロール/カント）を適用
                        graphicsHolder.rotateZDegrees(rollAngle);

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