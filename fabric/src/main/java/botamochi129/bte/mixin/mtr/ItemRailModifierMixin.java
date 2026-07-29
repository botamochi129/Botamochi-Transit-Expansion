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

        // クライアント側のプレビュー描画時のみ処理する
        ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
        if (world == null) return;

        int x1 = posStartMapped.getX();
        int y1 = posStartMapped.getY();
        int z1 = posStartMapped.getZ();
        int x2 = posEndMapped.getX();
        int y2 = posEndMapped.getY();
        int z2 = posEndMapped.getZ();

        double startRad = Math.toRadians(facingStart.angleDegrees);
        double endRad = Math.toRadians(facingEnd.angleDegrees);

        BlockPos p1 = new BlockPos(x1, y1, z1);
        BlockPos p2 = new BlockPos(x2, y2, z2);

        BlockEntity be1 = world.getBlockEntity(p1);
        if (be1 != null && be1.data instanceof StraightNodeBlockEntity sn1 && sn1.isBound()) {
            startRad = Math.toRadians(sn1.getAngleDegrees());
        }

        BlockEntity be2 = world.getBlockEntity(p2);
        if (be2 != null && be2.data instanceof StraightNodeBlockEntity sn2 && sn2.isBound()) {
            endRad = Math.toRadians(sn2.getAngleDegrees());
        }

        // 【修正】StraightNodeBlockEntity と同様、ブロック中心 (+0.5) を基準にする
        Vector startVec = new Vector(x1 + 0.5, y1, z1 + 0.5);
        Vector endVec = new Vector(x2 + 0.5, y2, z2 + 0.5);

        if (rail.railMath instanceof IRailMathExtra mathExtra) {
            mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad);
        }
    }
}