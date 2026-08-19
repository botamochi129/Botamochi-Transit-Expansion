package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.data.BezierCurve;
import botamochi129.bte.mod.data.IRailMathExtra;
import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mod.client.MinecraftClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "getRotation", at = @At("RETURN"), cancellable = true)
    private void bte$applyRollToCamera(CallbackInfoReturnable<Quaternionf> cir) {
        if (MinecraftClientData.getInstance() == null) return;

        var cameraEntity = net.minecraft.client.MinecraftClient.getInstance().getCameraEntity();
        if (cameraEntity == null) return;

        double playerX = cameraEntity.getX();
        double playerZ = cameraEntity.getZ();

        double rollRad = getCantAtPosition(playerX, playerZ);

        if (Math.abs(rollRad) > 0.001) {
            Quaternionf original = cir.getReturnValue();

            // ★ 修正: JOMLの rotateZ はラジアンを受け取るため、Math.toDegrees は不要！
            // そのままラジアン値を渡します。
            Quaternionf rollQuat = new Quaternionf().rotateZ((float) rollRad);

            // ローカルZ軸（視線方向）回りの回転を適用
            original.mul(rollQuat);
            cir.setReturnValue(original);
        }
    }

    @Unique
    private double getCantAtPosition(double x, double z) {
        MinecraftClientData data = MinecraftClientData.getInstance();
        if (data == null) return 0;

        Rail bestRail = null;
        double minDistSq = Double.MAX_VALUE;
        double bestRatio = 0;

        // data.rails へのアクセス保護
        Iterable<Rail> railsToCheck = null;
        try { railsToCheck = data.rails; } catch (Throwable ignored) {}
        if (railsToCheck == null) {
            java.util.List<Rail> flatList = new java.util.ArrayList<>();
            if (data.positionsToRail != null) {
                for (var map : data.positionsToRail.values()) {
                    if (map != null) flatList.addAll(map.values());
                }
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
            double startRoll = mathExtra.bte$getStartRoll();
            double endRoll = mathExtra.bte$getEndRoll();
            return startRoll + (endRoll - startRoll) * bestRatio;
        }
        return 0;
    }
}