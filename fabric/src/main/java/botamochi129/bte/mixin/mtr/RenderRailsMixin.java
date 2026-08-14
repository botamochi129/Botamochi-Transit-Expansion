package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.IRailMathExtra;
import botamochi129.bte.mod.data.NodeGeometry;
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

                // デフォルトはMTR標準の角度（標準ノード同士の接続用）
                Angle mtrStartAngle = rail.getStartAngle(pos1);
                Angle mtrEndAngle = rail.getStartAngle(pos2);
                double startRad = Math.toRadians(NodeGeometry.chooseBestExit(mtrStartAngle.angleDegrees, Math.toDegrees(Math.atan2(p2.getZ() - p1.getZ(), p2.getX() - p1.getX()))));
                double endRad = Math.toRadians(NodeGeometry.chooseBestExit(mtrEndAngle.angleDegrees, Math.toDegrees(Math.atan2(p1.getZ() - p2.getZ(), p1.getX() - p2.getX()))));
                boolean hasStraightNode = false;

                double offX1 = 0, offY1 = 0, offZ1 = 0;
                double offX2 = 0, offY2 = 0, offZ2 = 0;

                BlockEntity be1 = world.getBlockEntity(p1);
                if (be1 != null && be1.data instanceof StraightNodeBlockEntity sn1) {
                    offX1 = sn1.getOffsetX(); offY1 = sn1.getOffsetY(); offZ1 = sn1.getOffsetZ();
                    if (sn1.isBound()) {
                        double axis = sn1.getAngleDegrees();
                        double geo = Math.toDegrees(Math.atan2(p2.getZ() - p1.getZ(), p2.getX() - p1.getX()));
                        startRad = Math.toRadians(NodeGeometry.chooseBestExit(axis, geo));
                        hasStraightNode = true;
                    }
                }

                BlockEntity be2 = world.getBlockEntity(p2);
                if (be2 != null && be2.data instanceof StraightNodeBlockEntity sn2) {
                    offX2 = sn2.getOffsetX(); offY2 = sn2.getOffsetY(); offZ2 = sn2.getOffsetZ();
                    if (sn2.isBound()) {
                        double axis = sn2.getAngleDegrees();
                        double reverseGeo = Math.toDegrees(Math.atan2(p1.getZ() - p2.getZ(), p1.getX() - p2.getX()));
                        endRad = Math.toRadians(NodeGeometry.chooseBestExit(axis, reverseGeo));
                        hasStraightNode = true;
                    }
                }

                if (hasStraightNode) {
                    Vector startVec = new Vector(p1.getX() + 0.5 + offX1, p1.getY() + offY1, p1.getZ() + 0.5 + offZ1);
                    Vector endVec = new Vector(p2.getX() + 0.5 + offX2, p2.getY() + offY2, p2.getZ() + 0.5 + offZ2);

                    double verticalRadius = rail.railMath.getVerticalRadius();
                    Rail.Shape shape = rail.railMath.getShape();

                    long x1 = p1.getX(), y1 = p1.getY(), z1 = p1.getZ();
                    long x2 = p2.getX(), y2 = p2.getY(), z2 = p2.getZ();
                    long minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
                    long maxX = Math.max(x1, x2), maxY = Math.max(y1, y2), maxZ = Math.max(z1, z2);

                    String key = minX + "," + minY + "," + minZ + "," + maxX + "," + maxY + "," + maxZ;

                    double[] dataToSave = new double[]{
                            startVec.x(), startVec.y(), startVec.z(),
                            endVec.x(), endVec.y(), endVec.z(),
                            startRad, endRad,
                            verticalRadius,
                            shape.ordinal(),
                            (double) p1.getX(), (double) p1.getZ(),
                            (double) p2.getX(), (double) p2.getZ()
                    };

                    StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.put(key, dataToSave);

                    if (rail.railMath instanceof IRailMathExtra mathExtra) {
                        mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad, verticalRadius, shape);
                    }
                }
            });
        });
    }
}