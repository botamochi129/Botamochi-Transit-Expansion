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

                Angle mtrStartAngle = rail.getStartAngle(pos1);
                Angle mtrEndAngle = rail.getStartAngle(pos2);

                double startRad = Math.toRadians(mtrStartAngle.angleDegrees);
                double endRad = Math.toRadians(mtrEndAngle.angleDegrees);
                boolean hasStraightNode = false;

                // ★ 追加: オフセット値を格納する変数
                double offX1 = 0, offY1 = 0, offZ1 = 0;
                double offX2 = 0, offY2 = 0, offZ2 = 0;

                BlockEntity be1 = world.getBlockEntity(p1);
                if (be1 != null && be1.data instanceof StraightNodeBlockEntity sn1) {
                    // ★ 追加: オフセット値を取得
                    offX1 = sn1.getOffsetX();
                    offY1 = sn1.getOffsetY();
                    offZ1 = sn1.getOffsetZ();

                    if (sn1.isBound()) {
                        double rawDeg = sn1.getAngleDegrees();
                        if (!Angle.similarFacing(mtrStartAngle.angleDegrees, (float)rawDeg)) {
                            rawDeg += 180.0;
                        }
                        startRad = Math.toRadians(rawDeg);
                        hasStraightNode = true;
                    }
                }

                BlockEntity be2 = world.getBlockEntity(p2);
                if (be2 != null && be2.data instanceof StraightNodeBlockEntity sn2) {
                    // ★ 追加: オフセット値を取得
                    offX2 = sn2.getOffsetX();
                    offY2 = sn2.getOffsetY();
                    offZ2 = sn2.getOffsetZ();

                    if (sn2.isBound()) {
                        double rawDeg = sn2.getAngleDegrees();
                        if (!Angle.similarFacing(mtrEndAngle.angleDegrees, (float)rawDeg)) {
                            rawDeg += 180.0;
                        }
                        endRad = Math.toRadians(rawDeg);
                        hasStraightNode = true;
                    }
                }

                if (hasStraightNode) {
                    // ★ 修正: オフセット値を座標に加算する
                    Vector startVec = new Vector(p1.getX() + 0.5 + offX1, p1.getY() + offY1, p1.getZ() + 0.5 + offZ1);
                    Vector endVec = new Vector(p2.getX() + 0.5 + offX2, p2.getY() + offY2, p2.getZ() + 0.5 + offZ2);

                    double verticalRadius = rail.railMath.getVerticalRadius();
                    Rail.Shape shape = rail.railMath.getShape();

                    // キーはブロック座標(整数)で生成
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
                            shape.ordinal()
                    };

                    // 毎フレーム、クライアント側のマップを最新状態(オフセット適用済み)に更新
                    StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.put(key, dataToSave);

                    if (rail.railMath instanceof IRailMathExtra mathExtra) {
                        mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad, verticalRadius, shape);
                    }
                }
            });
        });
    }
}