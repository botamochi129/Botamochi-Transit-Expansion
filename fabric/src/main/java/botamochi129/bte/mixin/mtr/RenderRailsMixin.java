package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.IRailMathExtra;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.render.RenderRails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderRails.class, remap = false)
public abstract class RenderRailsMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private static void bte$patchRailsBeforeRender(CallbackInfo ci) {
        ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
        if (world == null) return;

        MinecraftClientData.getInstance().positionsToRail.forEach((pos1, map) -> {
            map.forEach((pos2, rail) -> {
                if (rail == null || rail.railMath == null) return;

                BlockPos p1 = Init.positionToBlockPos(pos1);
                BlockPos p2 = Init.positionToBlockPos(pos2);

                // MTRが計算した補正済みの角度を取得 (22.5度刻みだが、向きは正しい)
                Angle mtrStartAngle = rail.getStartAngle(pos1);
                Angle mtrEndAngle = rail.getStartAngle(pos2);

                double startRad = Math.toRadians(mtrStartAngle.angleDegrees);
                double endRad = Math.toRadians(mtrEndAngle.angleDegrees);
                boolean hasStraightNode = false;

                BlockEntity be1 = world.getBlockEntity(p1);
                if (be1 != null && be1.data instanceof StraightNodeBlockEntity sn1 && sn1.isBound()) {
                    double rawDeg = sn1.getAngleDegrees();
                    // MTRの論理角度と生の角度の「向き」を揃える
                    if (!Angle.similarFacing(mtrStartAngle.angleDegrees, (float)rawDeg)) {
                        rawDeg += 180.0;
                    }
                    startRad = Math.toRadians(rawDeg);
                    hasStraightNode = true;
                }

                BlockEntity be2 = world.getBlockEntity(p2);
                if (be2 != null && be2.data instanceof StraightNodeBlockEntity sn2 && sn2.isBound()) {
                    double rawDeg = sn2.getAngleDegrees();
                    // MTRの論理角度と生の角度の「向き」を揃える
                    if (!Angle.similarFacing(mtrEndAngle.angleDegrees, (float)rawDeg)) {
                        rawDeg += 180.0;
                    }
                    endRad = Math.toRadians(rawDeg);
                    hasStraightNode = true;
                }

                if (hasStraightNode) {
                    Vector startVec = new Vector(p1.getX() + 0.5, p1.getY(), p1.getZ() + 0.5);
                    Vector endVec = new Vector(p2.getX() + 0.5, p2.getY(), p2.getZ() + 0.5);

                    if (rail.railMath instanceof IRailMathExtra mathExtra) {
                        // 【重要修正】垂直半径を取得して渡す
                        double verticalRadius = rail.railMath.getVerticalRadius();
                        mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad, verticalRadius);
                    }
                }
            });
        });
    }
}