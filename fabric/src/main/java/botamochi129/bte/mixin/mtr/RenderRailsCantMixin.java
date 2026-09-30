package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.rail.RailCantSampler;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.render.RenderRails;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.RailResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * レール本体をカント（バンク）させる描画フック。
 *
 * <p>MTR のレール描画は断面ごとに高さを 1 つしか持たない（左右のレールが同じ高さ）ため、
 * そのままでは傾けられない。そこで
 * <ul>
 *   <li>床面（{@code IDrawing.drawTexture}）は頂点ごとに「進行方向に直交する左方向」の
 *       距離に比例した高さオフセットを与えて傾ける</li>
 *   <li>レール模型（{@code RailResource.render}）は区間中心でのカント角だけ Z 軸回転させる</li>
 * </ul>
 * の 2 段構えで表現する。どちらも区間中心のカント角を {@link RailCantSampler} から引く。
 *
 * <p>このファイルは 4.0.5+1.20.1 の {@code RenderRails} のメソッド名・ラムダ名に依存する。
 * 対象が見つからない場合は {@code require = 1} により起動時に明示的に失敗する。
 * {@code require = 0} にするとカントが出ないまま素通りするため、必ず 1 のままにすること。
 */
@Mixin(value = RenderRails.class, remap = false)
public abstract class RenderRailsCantMixin {

    @Unique
    private static final ThreadLocal<Rail> BTE_RENDERING_RAIL = new ThreadLocal<>();

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("HEAD"),
            remap = false,
            require = 1
    )
    private static void bte$captureCurrentRail(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float railWidth, CallbackInfo ci) {
        BTE_RENDERING_RAIL.set(rail);
    }

    @Inject(
            method = "renderRailStandard(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/core/data/Rail;Lorg/mtr/mod/render/RenderRails$RenderState;F)V",
            at = @At("TAIL"),
            remap = false,
            require = 1
    )
    private static void bte$clearCurrentRail(ClientWorld clientWorld, Rail rail, @Coerce Object renderState, float railWidth, CallbackInfo ci) {
        BTE_RENDERING_RAIL.remove();
    }

    @Redirect(
            method = "lambda$renderRailStandard$18(DDFDDDDDDDDFFFFFIILorg/mtr/mapping/mapper/GraphicsHolder;Lorg/mtr/mapping/holder/Vector3d;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/client/IDrawing;drawTexture(Lorg/mtr/mapping/mapper/GraphicsHolder;DDDDDDDDDDDDLorg/mtr/mapping/holder/Vector3d;FFFFLorg/mtr/mapping/holder/Direction;II)V"
            ),
            remap = false,
            require = 1
    )
    private static void bte$drawCantedRailSurface(
            GraphicsHolder graphicsHolder,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4,
            Vector3d playerOffset,
            float textureU1, float textureV1, float textureU2, float textureV2,
            Direction facing,
            int color,
            int light,
            double cornerX1, double cornerY1,
            float yOffset,
            double cornerZ1, double cornerX2, double cornerY2, double cornerZ2,
            double cornerX3, double cornerY3, double cornerZ3, double cornerX4,
            float u1, float v1, float u2, float v2, float railWidth,
            int lightIn,
            int overlayIn,
            GraphicsHolder graphicsHolderIn,
            Vector3d vector3d
    ) {
        final Rail rail = BTE_RENDERING_RAIL.get();
        if (rail == null) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }

        double forwardX = ((x3 + x4) - (x1 + x2)) * 0.5D;
        double forwardZ = ((z3 + z4) - (z1 + z2)) * 0.5D;
        if (Math.abs(forwardX) + Math.abs(forwardZ) < 1.0E-6D) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }

        final double centerX = (x1 + x2 + x3 + x4) * 0.25D;
        final double centerY = (y1 + y2 + y3 + y4) * 0.25D;
        final double centerZ = (z1 + z2 + z3 + z4) * 0.25D;

        final double cantDegrees = RailCantSampler.cantDegreesOnRail(rail, centerX, centerY, centerZ);
        if (Math.abs(cantDegrees) < 0.001D) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }

        // 局所の前進をレールの正規接線へ揃える。
        // 弦 (getPosition(0)→getPosition(length)) で判定するとカーブ中盤で接線と直交し符号が反転する。
        final double[] sample = RailCantSampler.sampleOnRail(rail, centerX, centerY, centerZ);
        if (sample == null) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }
        if ((sample[1] != 0.0D || sample[2] != 0.0D)
                && sample[1] * forwardX + sample[2] * forwardZ < 0.0D) {
            forwardX = -forwardX;
            forwardZ = -forwardZ;
        }

        double leftX = forwardZ;
        double leftZ = -forwardX;
        final double leftLength = Math.sqrt(leftX * leftX + leftZ * leftZ);
        if (leftLength < 1.0E-6D) {
            IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, playerOffset, textureU1, textureV1, textureU2, textureV2, facing, color, light);
            return;
        }
        leftX /= leftLength;
        leftZ /= leftLength;

        final double tanCant = Math.tan(Math.toRadians(cantDegrees));

        final double startCenterX = (x1 + x2) * 0.5D;
        final double startCenterZ = (z1 + z2) * 0.5D;
        final double endCenterX = (x3 + x4) * 0.5D;
        final double endCenterZ = (z3 + z4) * 0.5D;

        final double lateral1 = (x1 - startCenterX) * leftX + (z1 - startCenterZ) * leftZ;
        final double lateral2 = (x2 - startCenterX) * leftX + (z2 - startCenterZ) * leftZ;
        final double lateral3 = (x3 - endCenterX) * leftX + (z3 - endCenterZ) * leftZ;
        final double lateral4 = (x4 - endCenterX) * leftX + (z4 - endCenterZ) * leftZ;

        IDrawing.drawTexture(
                graphicsHolder,
                x1, y1 + tanCant * lateral1, z1,
                x2, y2 + tanCant * lateral2, z2,
                x3, y3 + tanCant * lateral3, z3,
                x4, y4 + tanCant * lateral4, z4,
                playerOffset,
                textureU1, textureV1, textureU2, textureV2,
                facing, color, light
        );
    }

    @Redirect(
            method = "lambda$renderRailStandard$16(Lorg/mtr/mapping/holder/ClientWorld;Lorg/mtr/mod/resource/RailResource;Z[ZLorg/mtr/mapping/holder/BlockPos;DDDDDDDDDD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/mtr/mod/resource/RailResource;render(Lorg/mtr/mod/render/StoredMatrixTransformations;I)V"
            ),
            remap = false,
            require = 1
    )
    private static void bte$renderCantedRailModel(
            RailResource invokedRailResource,
            StoredMatrixTransformations storedMatrixTransformations,
            int light,
            ClientWorld clientWorld,
            RailResource railResource,
            boolean flip,
            boolean[] renderType,
            org.mtr.mapping.holder.BlockPos blockPos,
            double x1, double z1,
            double x2, double z2,
            double x3, double z3,
            double x4, double z4,
            double y1, double y2
    ) {
        final double centerX = (x1 + x3) * 0.5D;
        final double centerY = (y1 + y2) * 0.5D;
        final double centerZ = (z1 + z3) * 0.5D;

        final Rail currentRail = BTE_RENDERING_RAIL.get();
        final double cantDegrees = currentRail == null ? 0.0D : RailCantSampler.cantDegreesOnRail(currentRail, centerX, centerY, centerZ);
        if (Math.abs(cantDegrees) < 0.001D) {
            invokedRailResource.render(storedMatrixTransformations, light);
            return;
        }

        final double signedCant = bte$signedCantDegrees(
                cantDegrees, currentRail, centerX, centerY, centerZ, x1, z1, x2, z2, x3, z3, x4, z4
        );
        // 模型が反転しているときは局所前進軸が逆を向くため、ワールド基準のカントを保つには符号を反す
        final double modelCant = flip ? -signedCant : signedCant;

        final StoredMatrixTransformations adjusted = storedMatrixTransformations.copy();
        adjusted.add(graphicsHolder -> graphicsHolder.rotateZDegrees((float) modelCant));
        invokedRailResource.render(adjusted, light);
    }

    @Unique
    private static double bte$signedCantDegrees(
            double cantDegrees,
            Rail rail,
            double centerX, double centerY, double centerZ,
            double x1, double z1,
            double x2, double z2,
            double x3, double z3,
            double x4, double z4
    ) {
        if (rail == null || Math.abs(cantDegrees) < 1.0E-6D) {
            return cantDegrees;
        }

        double forwardX = ((x3 + x4) - (x1 + x2)) * 0.5D;
        double forwardZ = ((z3 + z4) - (z1 + z2)) * 0.5D;
        final double widthX = ((x2 + x4) - (x1 + x3)) * 0.5D;
        final double widthZ = ((z2 + z4) - (z1 + z3)) * 0.5D;

        if (Math.abs(forwardX) + Math.abs(forwardZ) < 1.0E-6D
                || Math.abs(widthX) + Math.abs(widthZ) < 1.0E-6D) {
            return cantDegrees;
        }

        // 頂点順が start/end 入れ替わっていてもバンク方向が反転しないよう正規方向へ揃える。
        // 弦 (getPosition(0)→getPosition(length)) ではなく正規接線を使う。
        // カーブでは弦と局所接線が中盤で直交し、弦で判定すると符号が途中から反転する。
        final double[] sample = RailCantSampler.sampleOnRail(rail, centerX, centerY, centerZ);
        if (sample != null && (sample[1] != 0.0D || sample[2] != 0.0D)
                && sample[1] * forwardX + sample[2] * forwardZ < 0.0D) {
            forwardX = -forwardX;
            forwardZ = -forwardZ;
        }

        final double cross = forwardX * widthZ - forwardZ * widthX;
        return cross >= 0.0D ? cantDegrees : -cantDegrees;
    }
}
