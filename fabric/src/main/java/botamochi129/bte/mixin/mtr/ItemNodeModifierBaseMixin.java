package botamochi129.bte.mixin.mtr;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.data.AngleExtra;
import botamochi129.bte.mod.data.RailCalculator;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemUsageContext;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.holder.World;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.item.ItemNodeModifierBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemNodeModifierBase.class, remap = false)
public abstract class ItemNodeModifierBaseMixin {

    private static final String TAG_TRANSPORT_MODE = "transport_mode";

    @Shadow @Final protected boolean isConnector;

    @Shadow protected abstract void onConnect(World world, org.mtr.mapping.holder.ItemStack stack, TransportMode transportMode,
                                              BlockState state1, BlockState state2, BlockPos pos1, BlockPos pos2,
                                              Angle angle1, Angle angle2, ServerPlayerEntity player);

    @Shadow protected abstract void onRemove(World world, BlockPos pos1, BlockPos pos2, ServerPlayerEntity player);

    @Inject(method = "onEndClick", at = @At("HEAD"), cancellable = true)
    private void bte$onEndClick(ItemUsageContext context, BlockPos endBlockPos, CompoundTag tag, CallbackInfo ci) {
        World world = context.getWorld();
        BlockPos startBlockPos = context.getBlockPos();
        BlockState startState = world.getBlockState(startBlockPos);
        BlockState endState = world.getBlockState(endBlockPos);

        if (!(endState.getBlock().data instanceof BlockNode)) {
            return;
        }

        PlayerEntity player = context.getPlayer();
        if (!ServerPlayerEntity.isInstance(player)) return;
        if (!(startState.getBlock().data instanceof BlockNode)) return;

        BlockNode startNode = (BlockNode) startState.getBlock().data;
        if (!startNode.transportMode.toString().equals(tag.getString(TAG_TRANSPORT_MODE))) return;

        if (isConnector) {
            if (startBlockPos.equals(endBlockPos)) {
                tag.remove(TAG_TRANSPORT_MODE);
                ci.cancel();
                return;
            }

            float geoAngleDeg = (float) Math.toDegrees(Math.atan2(
                    endBlockPos.getZ() - startBlockPos.getZ(),
                    endBlockPos.getX() - startBlockPos.getX()
            ));

            Angle startAngle = AngleExtra.fromDegrees(BlockNode.getAngle(startState));
            Angle endAngle = AngleExtra.fromDegrees(BlockNode.getAngle(endState));

            StraightNodeBlockEntity beStart = getStraightNodeBE(world, startBlockPos);
            StraightNodeBlockEntity beEnd = getStraightNodeBE(world, endBlockPos);

            if (beStart != null && beEnd != null) {
                if (!beStart.isBound() && !beEnd.isBound()) {
                    beStart.bind(beEnd);
                }
            }

            boolean s1 = false, s2 = false;

            if (beStart == null) {
                s1 = true;
            } else {
                Angle f = beStart.getAngle();
                if (f == null) s1 = false;
                else {
                    startAngle = f;
                    s1 = true;
                }
            }

            if (beEnd == null) {
                s2 = true;
            } else {
                Angle f = beEnd.getAngle();
                if (f == null) s2 = false;
                else {
                    endAngle = f;
                    s2 = true;
                }
            }

            if (s1 && !s2) {
                Double deg = RailCalculator.calculateMaxRadiusAngle(
                        startBlockPos.getX(), startBlockPos.getZ(),
                        endBlockPos.getX(), endBlockPos.getZ(),
                        Math.toRadians(startAngle.angleDegrees)
                );
                if (deg == null) {
                    if (player != null) {
                        player.sendMessage(Text.of("Invalid orientation"), true);
                    }
                    tag.remove(TAG_TRANSPORT_MODE);
                    ci.cancel();
                    return;
                } else {
                    beEnd.bind(deg);
                    endAngle = AngleExtra.fromDegrees(deg);
                    s2 = true;
                    if (player != null) {
                        player.sendMessage(Text.of("Bound straight node"), true);
                    }
                }
            } else if (s2 && !s1) {
                Double deg = RailCalculator.calculateMaxRadiusAngle(
                        endBlockPos.getX(), endBlockPos.getZ(),
                        startBlockPos.getX(), startBlockPos.getZ(),
                        Math.toRadians(endAngle.angleDegrees)
                );
                if (deg == null) {
                    if (player != null) {
                        player.sendMessage(Text.of("Invalid orientation"), true);
                    }
                    tag.remove(TAG_TRANSPORT_MODE);
                    ci.cancel();
                    return;
                } else {
                    beStart.bind(deg);
                    startAngle = AngleExtra.fromDegrees(deg);
                    s1 = true;
                    if (player != null) {
                        player.sendMessage(Text.of("Bound straight node"), true);
                    }
                }
            }

            if (s1 && s2) {
                if (!Angle.similarFacing(geoAngleDeg, startAngle.angleDegrees)) {
                    startAngle = AngleExtra.fromDegrees(startAngle.angleDegrees + 180);
                }
                if (Angle.similarFacing(geoAngleDeg, endAngle.angleDegrees)) {
                    endAngle = AngleExtra.fromDegrees(endAngle.angleDegrees + 180);
                }

                onConnect(
                        world,
                        context.getStack(),
                        startNode.transportMode,
                        startState,
                        endState,
                        startBlockPos,
                        endBlockPos,
                        startAngle,
                        endAngle,
                        ServerPlayerEntity.cast(player)
                );
            } else {
                if (player != null) {
                    player.sendMessage(Text.of("Unbound straight node"), true);
                }
            }
        } else {
            onRemove(world, startBlockPos, endBlockPos, ServerPlayerEntity.cast(player));
        }

        tag.remove(TAG_TRANSPORT_MODE);
        ci.cancel();
    }

    private static StraightNodeBlockEntity getStraightNodeBE(World world, BlockPos pos) {
        org.mtr.mapping.holder.BlockEntity rawBe = world.getBlockEntity(pos);
        if (rawBe != null && rawBe.data instanceof StraightNodeBlockEntity be) {
            return be;
        }
        return null;
    }
}
