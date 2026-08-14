package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.IRailMathExtra;
import botamochi129.bte.mod.data.NodeGeometry; // ★ 追加: ジオメトリユーティリティ
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.block.BlockNode; // ★ 追加: 標準ノード角度取得用
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

        BlockPos p1 = new BlockPos(x1, y1, z1);
        BlockPos p2 = new BlockPos(x2, y2, z2);

        // 1. オフセットと固定角度（バインド済み or 標準ノード）の取得
        double offX1 = 0, offY1 = 0, offZ1 = 0;
        double offX2 = 0, offY2 = 0, offZ2 = 0;
        Double fixedStart = null;
        Double fixedEnd = null;

        BlockEntity be1 = world.getBlockEntity(p1);
        if (be1 != null && be1.data instanceof StraightNodeBlockEntity sn1) {
            offX1 = sn1.getOffsetX(); offY1 = sn1.getOffsetY(); offZ1 = sn1.getOffsetZ();
            if (sn1.isBound()) fixedStart = sn1.getAngleDegrees();
        } else if (be1 == null) {
            fixedStart = (double) BlockNode.getAngle(stateStart); // MTR標準ノード
        }

        BlockEntity be2 = world.getBlockEntity(p2);
        if (be2 != null && be2.data instanceof StraightNodeBlockEntity sn2) {
            offX2 = sn2.getOffsetX(); offY2 = sn2.getOffsetY(); offZ2 = sn2.getOffsetZ();
            if (sn2.isBound()) fixedEnd = sn2.getAngleDegrees();
        } else if (be2 == null) {
            fixedEnd = (double) BlockNode.getAngle(stateEnd);
        }

        // 2. 角度決定ロジック (ItemNodeModifierBaseMixin と完全に同じ基準)
        double nodeStartDeg;
        if (fixedStart != null) {
            nodeStartDeg = fixedStart;
        } else if (fixedEnd != null) {
            nodeStartDeg = NodeGeometry.maxRadiusTangentAngle(p2, fixedEnd, p1);
        } else {
            nodeStartDeg = NodeGeometry.straightAngle(p1, p2);
        }

        double nodeEndDeg;
        if (fixedEnd != null) {
            nodeEndDeg = fixedEnd;
        } else if (fixedStart != null) {
            nodeEndDeg = NodeGeometry.maxRadiusTangentAngle(p1, fixedStart, p2);
        } else {
            nodeEndDeg = NodeGeometry.straightAngle(p2, p1);
        }

        // 3. レール用の向き補正 (±180°)
        double geoAngle = NodeGeometry.straightAngle(p1, p2);
        double railStartDeg = NodeGeometry.normalizeDegrees(nodeStartDeg + (Angle.similarFacing((float) geoAngle, (float) nodeStartDeg) ? 0 : 180));
        double railEndDeg = NodeGeometry.normalizeDegrees(nodeEndDeg + (Angle.similarFacing((float) geoAngle, (float) nodeEndDeg) ? 180 : 0));

        // 4. ベジェ適用 (オフセット適用済み座標を使用)
        // ※ X/Zはブロック中心(+0.5), Yはブロック底面(+0)を基準とする
        Vector startVec = new Vector(x1 + 0.5 + offX1, y1 + offY1, z1 + 0.5 + offZ1);
        Vector endVec = new Vector(x2 + 0.5 + offX2, y2 + offY2, z2 + 0.5 + offZ2);

        if (rail.railMath instanceof IRailMathExtra mathExtra) {
            double verticalRadius = rail.railMath.getVerticalRadius();
            mathExtra.bte$enableBezier(
                    startVec,
                    Math.toRadians(railStartDeg),
                    endVec,
                    Math.toRadians(railEndDeg),
                    verticalRadius,
                    rail.railMath.getShape()
            );
        }
    }
}