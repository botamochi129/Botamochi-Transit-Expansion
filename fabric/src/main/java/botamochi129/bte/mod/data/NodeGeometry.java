package botamochi129.bte.mod.data;

import org.mtr.mapping.holder.BlockPos;

public final class NodeGeometry {

    public static double straightAngle(BlockPos a, BlockPos b) {
        return normalizeDegrees(Math.toDegrees(Math.atan2(b.getZ() - a.getZ(), b.getX() - a.getX())));
    }

    public static double maxRadiusTangentAngle(BlockPos fixed, double fixedAngle, BlockPos free) {
        double fx = fixed.getX(), fz = fixed.getZ();
        double px = free.getX(), pz = free.getZ();
        double rad = Math.toRadians(fixedAngle);
        double dirX = Math.cos(rad), dirZ = Math.sin(rad);
        double nX = -dirZ, nZ = dirX;
        double dX = px - fx, dZ = pz - fz;
        double nDotD = nX * dX + nZ * dZ;
        if (Math.abs(nDotD) < 1e-6) return straightAngle(fixed, free);
        double t = (dX * dX + dZ * dZ) / (2 * nDotD);
        double cx = fx + t * nX, cz = fz + t * nZ;
        double rX = px - cx, rZ = pz - cz;
        double tangX = -rZ, tangZ = rX;
        double fixTangX = -(fz - cz), fixTangZ = (fx - cx);
        if (fixTangX * dirX + fixTangZ * dirZ >= 0) { tangX = -tangX; tangZ = -tangZ; }
        return normalizeDegrees(Math.toDegrees(Math.atan2(tangZ, tangX)));
    }

    public static double normalizeDegrees(double deg) {
        deg %= 360.0;
        if (deg < 0) deg += 360.0;
        return deg;
    }

    // ★ 追加: 2つの角度が同じ向き（±90°以内）を向いているか判定
    public static boolean isFacingDirection(double nodeAngle, double connectionAngle) {
        double diff = Math.abs(nodeAngle - connectionAngle) % 360.0;
        if (diff > 180.0) diff = 360.0 - diff;
        return diff < 90.0;
    }

    // ★ 追加: 軸 (0~180°) から、ターゲット方向に最も近い出口 (0~360°) を選択する
    public static double chooseBestExit(double axisDeg, double targetGeoDeg) {
        double c1 = normalizeDegrees(axisDeg);
        double c2 = normalizeDegrees(axisDeg + 180.0);
        return isFacingDirection(c1, targetGeoDeg) ? c1 : c2;
    }
}