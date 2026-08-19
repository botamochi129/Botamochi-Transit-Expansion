package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.BezierCurve;
import botamochi129.bte.mod.data.IRailMathExtra;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicles;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderVehicles.class, remap = false)
public abstract class RenderVehiclesMixin {

    @Inject(method = "getStoredMatrixTransformations(ZLorg/mtr/mod/render/PositionAndRotation;D)Lorg/mtr/mod/render/StoredMatrixTransformations;", at = @At("RETURN"))
    private static void bte$applyRollToMatrix(boolean useOffset, PositionAndRotation renderingPositionAndRotation, double oscillationAmount, CallbackInfoReturnable<StoredMatrixTransformations> cir) {
        if (renderingPositionAndRotation == null) return;

        StoredMatrixTransformations transforms = cir.getReturnValue();
        if (transforms == null) return;

        double x = renderingPositionAndRotation.position.x;
        double z = renderingPositionAndRotation.position.z;

        double trainYaw = renderingPositionAndRotation.yaw;
        double trainDirX = Math.sin(trainYaw);
        double trainDirZ = Math.cos(trainYaw);

        double[] rollAndDir = getCantAndDirAtPosition(x, z);
        double rollRad = rollAndDir[0];
        double railDirX = rollAndDir[1];
        double railDirZ = rollAndDir[2];

        if (Math.abs(rollRad) > 0.001) {
            double dot = trainDirX * railDirX + trainDirZ * railDirZ;
            if (dot < 0) {
                rollRad = -rollRad;
            }

            final double finalRoll = rollRad;
            transforms.add(graphicsHolder -> {
                graphicsHolder.rotateZRadians((float) finalRoll);
            });
        }
    }

    @Unique
    private static double[] getCantAndDirAtPosition(double x, double z) {
        MinecraftClientData data = MinecraftClientData.getInstance();
        if (data == null) return new double[]{0, 1, 0};

        Rail bestRail = null;
        double minDistSq = Double.MAX_VALUE;
        double bestRatio = 0;

        // ★ 修正: data.rails が存在しない場合、positionsToRail から展開する
        Iterable<Rail> railsToCheck = null;
        try {
            railsToCheck = data.rails;
        } catch (Throwable ignored) {}

        if (railsToCheck == null) {
            java.util.List<Rail> flatList = new java.util.ArrayList<>();
            for (var map : data.positionsToRail.values()) {
                if (map != null) flatList.addAll(map.values());
            }
            railsToCheck = flatList;
        }

        for (Rail rail : railsToCheck) {
            if (rail == null || rail.railMath == null) continue;
            if (!(rail.railMath instanceof IRailMathExtra mathExtra) || !mathExtra.bte$isBezierEnabled()) continue;

            if (x < rail.railMath.minX - 2 || x > rail.railMath.maxX + 2) continue;
            if (z < rail.railMath.minZ - 2 || z > rail.railMath.maxZ + 2) continue;

            BezierCurve curve = mathExtra.bte$getCurve();
            if (curve == null) continue;

            double length = curve.getLength();
            if (length <= 0) continue;

            int steps = 10;
            for (int i = 0; i <= steps; i++) {
                double dist = (length * i) / steps;
                Vector p = curve.getPosition(dist);
                double distSq = (p.x() - x) * (p.x() - x) + (p.z() - z) * (p.z() - z);
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    bestRail = rail;
                    bestRatio = (double) i / steps;
                }
            }
        }

        if (bestRail != null && bestRail.railMath instanceof IRailMathExtra mathExtra) {
            BezierCurve curve = mathExtra.bte$getCurve();
            double startRoll = mathExtra.bte$getStartRoll();
            double endRoll = mathExtra.bte$getEndRoll();
            double roll = startRoll + (endRoll - startRoll) * bestRatio;

            Vector tangent = curve.getTangent(bestRatio);
            double len = Math.sqrt(tangent.x() * tangent.x() + tangent.z() * tangent.z());
            double dirX = len > 0 ? tangent.x() / len : 1;
            double dirZ = len > 0 ? tangent.z() / len : 0;

            return new double[]{roll, dirX, dirZ};
        }
        return new double[]{0, 1, 0};
    }
}