package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.IRailExtra;
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

    /**
     * 毎フレームの描画前に、MinecraftClientData にあるレールをチェックし、
     * StraightNode が含まれていればベジェ曲線を適用する
     */
    @Inject(method = "render", at = @At("HEAD"))
    private static void bte$patchRailsBeforeRender(CallbackInfo ci) {
        ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
        if (world == null) return;

        // positionsToRail を走査して Rail オブジェクトを取得
        MinecraftClientData.getInstance().positionsToRail.forEach((pos1, map) -> {
            map.forEach((pos2, rail) -> {
                if (rail == null || rail.railMath == null) return;

                // Position から BlockPos へ変換
                BlockPos p1 = Init.positionToBlockPos(pos1);
                BlockPos p2 = Init.positionToBlockPos(pos2);

                // Rail クラスの getStartAngle(Position) を使用して正しい角度を取得
                double startRad = Math.toRadians(rail.getStartAngle(pos1).angleDegrees);
                double endRad = Math.toRadians(rail.getStartAngle(pos2).angleDegrees);
                boolean hasStraightNode = false;

                BlockEntity be1 = world.getBlockEntity(p1);
                if (be1 != null && be1.data instanceof StraightNodeBlockEntity sn1 && sn1.isBound()) {
                    startRad = Math.toRadians(sn1.getAngleDegrees());
                    hasStraightNode = true;
                }

                BlockEntity be2 = world.getBlockEntity(p2);
                if (be2 != null && be2.data instanceof StraightNodeBlockEntity sn2 && sn2.isBound()) {
                    endRad = Math.toRadians(sn2.getAngleDegrees());
                    hasStraightNode = true;
                }

                if (hasStraightNode) {
                    Vector startVec = new Vector(p1.getX(), p1.getY(), p1.getZ());
                    Vector endVec = new Vector(p2.getX(), p2.getY(), p2.getZ());

                    // ★ 修正: railMath に直接アクセス
                    if (rail.railMath instanceof IRailMathExtra mathExtra) {
                        mathExtra.bte$enableBezier(startVec, startRad, endVec, endRad);
                    }
                }
            });
        });
    }
}