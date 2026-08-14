package botamochi129.bte.mod.data;

import org.mtr.mapping.holder.BlockPos;

public final class NodeGeometry {

    /** 両端の水平直線角（直線優先・半径0） */
    public static double straightAngle(BlockPos a, BlockPos b) {
        return normalizeDegrees(Math.toDegrees(Math.atan2(b.getZ() - a.getZ(), b.getX() - a.getX())));
    }

    /** 固定端(fixed, fixedAngle)に接する最大半径円弧の、自由端(free)における接線角 */
    public static double maxRadiusTangentAngle(BlockPos fixed, double fixedAngle, BlockPos free) {
        double fx = fixed.getX(), fz = fixed.getZ();
        double px = free.getX(), pz = free.getZ();
        double rad = Math.toRadians(fixedAngle);
        double dirX = Math.cos(rad), dirZ = Math.sin(rad);
        double nX = -dirZ, nZ = dirX;
        double dX = px - fx, dZ = pz - fz;
        double nDotD = nX * dX + nZ * dZ;
        if (Math.abs(nDotD) < 1e-6) return straightAngle(fixed, free); // 共線→直線
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
}