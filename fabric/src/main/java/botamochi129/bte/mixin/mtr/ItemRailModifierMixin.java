package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.IRailMathExtra;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.item.ItemRailModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = ItemRailModifier.class, remap = false)
public abstract class ItemRailModifierMixin {

    @Inject(method = "createRail", at = @At("RETURN"))
    private static void bte$applyCustomBezierToPreview(
            UUID uuid, TransportMode transportMode,
            BlockState stateStart, BlockState stateEnd,
            BlockPos posStartMapped, BlockPos posEndMapped,
            Angle facingStart, Angle facingEnd,
            CallbackInfoReturnable<Rail> cir
    ) {
        Rail rail = cir.getReturnValue();
        if (rail == null || rail.railMath == null) return;

        ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
        if (world == null) return;

        int x1 = posStartMapped.getX();
        int y1 = posStartMapped.getY();
        int z1 = posStartMapped.getZ();
        int x2 = posEndMapped.getX();
        int y2 = posEndMapped.getY();
        int z2 = posEndMapped.getZ();

        // 幾何学的な進行方向を計算
        double geoAngleStart = Math.toDegrees(Math.atan2(z2 - z1, x2 - x1));
        double geoAngleEnd = Math.toDegrees(Math.atan2(z1 - z2, x1 - x2));

        geoAngleStart = normalize360(geoAngleStart);
        geoAngleEnd = normalize360(geoAngleEnd);

        double startDeg = facingStart.angleDegrees;
        double endDeg = facingEnd.angleDegrees;

        BlockPos p1 = new BlockPos(x1, y1, z1);
        BlockPos p2 = new BlockPos(x2, y2, z2);

        BlockEntity be1 = world.getBlockEntity(p1);
        if (be1 != null && be1.data instanceof StraightNodeBlockEntity sn1) {
            if (sn1.isBound()) {
                startDeg = sn1.getAngleDegrees();
            } else {
                if (!Angle.similarFacing((float) geoAngleStart, (float) startDeg)) {
                    startDeg = normalize360(startDeg + 180.0);
                }
            }
        }

        BlockEntity be2 = world.getBlockEntity(p2);
        if (be2 != null && be2.data instanceof StraightNodeBlockEntity sn2) {
            if (sn2.isBound()) {
                endDeg = sn2.getAngleDegrees();
            } else {
                if (!Angle.similarFacing((float) geoAngleEnd, (float) endDeg)) {
                    endDeg = normalize360(endDeg + 180.0);
                }
            }
        }

        Vector startVec = new Vector(x1 + 0.5, y1, z1 + 0.5);
        Vector endVec = new Vector(x2 + 0.5, y2, z2 + 0.5);

        if (rail.railMath instanceof IRailMathExtra mathExtra) {
            // 【重要修正】rail から verticalRadius を取得し、5引数で呼び出す
            double verticalRadius = rail.railMath.getVerticalRadius();
            mathExtra.bte$enableBezier(
                    startVec,
                    Math.toRadians(startDeg),
                    endVec,
                    Math.toRadians(endDeg),
                    verticalRadius
            );
        }
    }

    private static double normalize360(double angle) {
        angle = angle % 360.0;
        if (angle < 0.0) angle += 360.0;
        return angle;
    }
}