package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.IRailMathExtra;
import botamochi129.bte.mod.data.NodeGeometry;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mod.block.BlockNode;
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

        int x1 = posStartMapped.getX(), y1 = posStartMapped.getY(), z1 = posStartMapped.getZ();
        int x2 = posEndMapped.getX(), y2 = posEndMapped.getY(), z2 = posEndMapped.getZ();
        BlockPos p1 = new BlockPos(x1, y1, z1);
        BlockPos p2 = new BlockPos(x2, y2, z2);

        double offX1 = 0, offY1 = 0, offZ1 = 0;
        double offX2 = 0, offY2 = 0, offZ2 = 0;
        Double fixedStart = null;
        Double fixedEnd = null;

        BlockEntity be1 = world.getBlockEntity(p1);
        if (be1 != null && be1.data instanceof StraightNodeBlockEntity sn1) {
            offX1 = sn1.getOffsetX(); offY1 = sn1.getOffsetY(); offZ1 = sn1.getOffsetZ();
            if (sn1.isBound()) fixedStart = sn1.getAngleDegrees();
        } else if (be1 == null) {
            fixedStart = (double) BlockNode.getAngle(stateStart);
        }

        BlockEntity be2 = world.getBlockEntity(p2);
        if (be2 != null && be2.data instanceof StraightNodeBlockEntity sn2) {
            offX2 = sn2.getOffsetX(); offY2 = sn2.getOffsetY(); offZ2 = sn2.getOffsetZ();
            if (sn2.isBound()) fixedEnd = sn2.getAngleDegrees();
        } else if (be2 == null) {
            fixedEnd = (double) BlockNode.getAngle(stateEnd);
        }

        double geo = NodeGeometry.straightAngle(p1, p2);
        double reverseGeo = NodeGeometry.straightAngle(p2, p1);

        // ★ 修正: すべてのノード（標準ノード含む）に対して chooseBestExit を適用
        double nodeStartDeg;
        if (fixedStart != null) {
            nodeStartDeg = NodeGeometry.chooseBestExit(fixedStart, geo);
        } else if (fixedEnd != null) {
            double endExit = NodeGeometry.chooseBestExit(fixedEnd, reverseGeo);
            nodeStartDeg = NodeGeometry.maxRadiusTangentAngle(p2, endExit, p1);
        } else {
            nodeStartDeg = geo;
        }

        double nodeEndDeg;
        if (fixedEnd != null) {
            nodeEndDeg = NodeGeometry.chooseBestExit(fixedEnd, reverseGeo);
        } else if (fixedStart != null) {
            double startExit = NodeGeometry.chooseBestExit(fixedStart, geo);
            nodeEndDeg = NodeGeometry.maxRadiusTangentAngle(p1, startExit, p2);
        } else {
            nodeEndDeg = reverseGeo;
        }

        Vector startVec = new Vector(x1 + 0.5 + offX1, y1 + offY1, z1 + 0.5 + offZ1);
        Vector endVec = new Vector(x2 + 0.5 + offX2, y2 + offY2, z2 + 0.5 + offZ2);

        if (rail.railMath instanceof IRailMathExtra mathExtra) {
            double verticalRadius = rail.railMath.getVerticalRadius();
            mathExtra.bte$enableBezier(
                    startVec, Math.toRadians(nodeStartDeg), endVec, Math.toRadians(nodeEndDeg),
                    verticalRadius, rail.railMath.getShape()
            );
        }
    }
}