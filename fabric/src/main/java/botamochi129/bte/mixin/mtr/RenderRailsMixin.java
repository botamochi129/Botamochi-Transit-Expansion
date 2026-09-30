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

    /**
     * 描画の直前に BTE ノードの.Exit 角度を {@link StraightNodeBlockEntity#RAIL_MATH_DATA_MAP} と
     * {@link IRailMathExtra} へ反映する。
     * <p>
     * ★ 軽量化: 旧実装は毎フレーム「全レール」に対して {@code atan2} 2 本と
     * {@code rail.getStartAngle} 2 本を計算してから BTE ノードの有無を判定していた。
     * MTR の线路の大半は標準ノードだけなので、その計算はほぼ無駄になる。
     * 判定を「両端が束縛済み BTE ノードか」の {@link BlockEntity} 取得だけに先行させ、
     * 該当しないレールは即 return する。
     */
    @Inject(method = "render", at = @At("HEAD"))
    private static void bte$patchRailsBeforeRender(CallbackInfo ci) {
        ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
        if (world == null) return;

        MinecraftClientData.getInstance().positionsToRail.forEach((pos1, map) -> {
            map.forEach((pos2, rail) -> {
                if (rail == null || rail.railMath == null) return;

                BlockPos p1 = Init.positionToBlockPos(pos1);
                BlockPos p2 = Init.positionToBlockPos(pos2);

                // ── 早期終了: BTE ノード怎样才能どちらの端にも無ければ何もしない ──
                BlockEntity be1 = world.getBlockEntity(p1);
                BlockEntity be2 = world.getBlockEntity(p2);

                StraightNodeBlockEntity sn1 = (be1 != null && be1.data instanceof StraightNodeBlockEntity s) ? s : null;
                StraightNodeBlockEntity sn2 = (be2 != null && be2.data instanceof StraightNodeBlockEntity s) ? s : null;

                final boolean bound1 = sn1 != null && sn1.isBound();
                final boolean bound2 = sn2 != null && sn2.isBound();
                if (!bound1 && !bound2) return;

                double geo = Math.toDegrees(Math.atan2(p2.getZ() - p1.getZ(), p2.getX() - p1.getX()));
                double reverseGeo = Math.toDegrees(Math.atan2(p1.getZ() - p2.getZ(), p1.getX() - p2.getX()));

                // デフォルトは MTR 標準の角度
                Angle mtrStartAngle = rail.getStartAngle(pos1);
                Angle mtrEndAngle = rail.getStartAngle(pos2);

                double startRad = Math.toRadians(NodeGeometry.chooseBestExit(mtrStartAngle.angleDegrees, geo));
                double endRad = Math.toRadians(NodeGeometry.chooseBestExit(mtrEndAngle.angleDegrees, reverseGeo));

                double offX1 = 0, offY1 = 0, offZ1 = 0;
                double offX2 = 0, offY2 = 0, offZ2 = 0;

                if (sn1 != null) {
                    offX1 = sn1.getOffsetX(); offY1 = sn1.getOffsetY(); offZ1 = sn1.getOffsetZ();
                    if (bound1) startRad = Math.toRadians(NodeGeometry.chooseBestExit(sn1.getAngleDegrees(), geo));
                }

                if (sn2 != null) {
                    offX2 = sn2.getOffsetX(); offY2 = sn2.getOffsetY(); offZ2 = sn2.getOffsetZ();
                    if (bound2) endRad = Math.toRadians(NodeGeometry.chooseBestExit(sn2.getAngleDegrees(), reverseGeo));
                }

                double verticalRadius = rail.railMath.getVerticalRadius();
                Rail.Shape shape = rail.railMath.getShape();

                Vector startVec = new Vector(p1.getX() + 0.5 + offX1, p1.getY() + offY1, p1.getZ() + 0.5 + offZ1);
                Vector endVec = new Vector(p2.getX() + 0.5 + offX2, p2.getY() + offY2, p2.getZ() + 0.5 + offZ2);

                String key = StraightNodeBlockEntity.railMathKey(
                        p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ()
                );

                // ★ 差分書き込み: 既に同じ値が入っていれば double[14] と Map を触らない。
                //   毎フレーム new していた分を、値が動いたときだけにする。
                double[] existing = StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.get(key);
                if (existing == null || existing.length < 14
                        || existing[0] != startVec.x() || existing[1] != startVec.y() || existing[2] != startVec.z()
                        || existing[3] != endVec.x() || existing[4] != endVec.y() || existing[5] != endVec.z()
                        || existing[6] != startRad || existing[7] != endRad
                        || existing[8] != verticalRadius || (int) existing[9] != shape.ordinal()
                        || existing[10] != p1.getX() || existing[11] != p1.getZ()
                        || existing[12] != p2.getX() || existing[13] != p2.getZ()) {
                    StraightNodeBlockEntity.RAIL_MATH_DATA_MAP.put(key, new double[]{
                            startVec.x(), startVec.y(), startVec.z(),
                            endVec.x(), endVec.y(), endVec.z(),
                            startRad, endRad,
                            verticalRadius, shape.ordinal(),
                            (double) p1.getX(), (double) p1.getZ(),
                            (double) p2.getX(), (double) p2.getZ()
                    });
                }

                if (rail.railMath instanceof IRailMathExtra mathExtra) {
                    mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad, verticalRadius, shape);
                }
            });
        });
    }
}
