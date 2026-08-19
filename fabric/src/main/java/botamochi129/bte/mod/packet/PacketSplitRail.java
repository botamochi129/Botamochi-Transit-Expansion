package botamochi129.bte.mod.packet;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.registry.Blocks;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class PacketSplitRail extends PacketHandler {

    private final String railHexId;
    private final double splitDistance;

    public PacketSplitRail(PacketBufferReceiver receiver) {
        this.railHexId = receiver.readString();
        this.splitDistance = receiver.readDouble();
    }

    public PacketSplitRail(String railHexId, double splitDistance) {
        this.railHexId = railHexId;
        this.splitDistance = splitDistance;
    }

    @Override
    public void write(PacketBufferSender packetBufferSender) {
        packetBufferSender.writeString(railHexId);
        packetBufferSender.writeDouble(splitDistance);
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Object obj, String fieldName) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return (T) field.get(obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void runServer(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity) {
        minecraftServer.execute(() -> {
            try {
                // 1. Simulator (TSC) インスタンスを取得
                Field mainField = Class.forName("org.mtr.mod.Init").getDeclaredField("main");
                mainField.setAccessible(true);
                Object mainInstance = mainField.get(null);

                Field simulatorsField = mainInstance.getClass().getDeclaredField("simulators");
                simulatorsField.setAccessible(true);
                List<?> simulators = (List<?>) simulatorsField.get(mainInstance);

                if (simulators.isEmpty()) return;
                Object simulator = simulators.get(0);

                // 2. rails リストを取得
                Field railsField = simulator.getClass().getSuperclass().getDeclaredField("rails");
                railsField.setAccessible(true);
                org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet<Rail> rails =
                        (org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet<Rail>) railsField.get(simulator);

                // 3. 対象のレールを検索
                Rail targetRail = null;
                for (Rail r : rails) {
                    if (r.getHexId().equals(railHexId)) {
                        targetRail = r;
                        break;
                    }
                }

                if (targetRail == null) return;

                // 4. 分割点の計算 (Vector)
                Vector splitVec = targetRail.railMath.getPosition(splitDistance, false);
                double railLength = targetRail.railMath.getLength();

                // 接線ベクトル（角度）の計算
                double epsilon = Math.min(0.5, railLength / 100.0);
                double distA = Math.max(0, splitDistance - epsilon);
                double distB = Math.min(railLength, splitDistance + epsilon);

                Vector vecA = targetRail.railMath.getPosition(distA, false);
                Vector vecB = targetRail.railMath.getPosition(distB, false);

                double dx = vecB.x() - vecA.x();
                double dz = vecB.z() - vecA.z();

                if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) {
                    System.out.println("WARNING: dx and dz are both 0. Split position may be too close to the end.");
                    return;
                }

                float splitAngleDegrees = (float) Math.toDegrees(Math.atan2(dz, dx));
                splitAngleDegrees = ((splitAngleDegrees % 360.0f) + 360.0f) % 360.0f;
                if (splitAngleDegrees > 180.0f) splitAngleDegrees -= 360.0f;
                splitAngleDegrees = Math.round(splitAngleDegrees * 10.0f) / 10.0f;

                // 5. ノード設置座標 (BlockPos) の決定
                int bx = (int) Math.round(splitVec.x());
                int by = (int) Math.round(splitVec.y());
                int bz = (int) Math.round(splitVec.z());
                net.minecraft.util.math.BlockPos nativeBlockPos = new net.minecraft.util.math.BlockPos(bx, by, bz);

                // 6. ノードブロックの設置
                net.minecraft.server.world.ServerWorld nativeServerWorld =
                        (net.minecraft.server.world.ServerWorld) serverPlayerEntity.getEntityWorld().data;

                org.mtr.mapping.holder.Block bteBlock = Blocks.STRAIGHT_NODE.get();
                net.minecraft.block.Block nativeBlock = (net.minecraft.block.Block) bteBlock.data;

                nativeServerWorld.setBlockState(nativeBlockPos, nativeBlock.getDefaultState(), 3);

                // 7. BlockEntityの取得と設定 (角度・オフセット)
                net.minecraft.block.entity.BlockEntity nativeBe = nativeServerWorld.getBlockEntity(nativeBlockPos);
                if (nativeBe instanceof StraightNodeBlockEntity bteBe) {
                    bteBe.bind(splitAngleDegrees);

                    // ★ オフセットを精密に設定し、視覚的なズレを最小限にする
                    double offX = splitVec.x() - bx;
                    double offY = splitVec.y() - by;
                    double offZ = splitVec.z() - bz;
                    bteBe.setOffset(offX, offY, offZ);

                    bteBe.markDirty2();
                }

                // 8. MTRの Position オブジェクトを作成
                Position splitPos = Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(nativeBlockPos));

                // 9. 元のレールを削除
                rails.remove(targetRail);

                // 10. プロパティ取得
                Position pos1 = getField(targetRail, "position1");
                Position pos2 = getField(targetRail, "position2");
                Rail.Shape shape = getField(targetRail, "shape");
                double originalVerticalRadius = getField(targetRail, "verticalRadius");
                boolean canHaveSignal = getField(targetRail, "canHaveSignal");

                Angle angle1 = targetRail.getStartAngle(false);
                Angle angle2 = targetRail.getStartAngle(true);

                ObjectArrayList<String> styles = new ObjectArrayList<>();
                styles.addAll(targetRail.getStyles());

                long speedLimit1 = targetRail.getSpeedLimitKilometersPerHour(false);
                long speedLimit2 = targetRail.getSpeedLimitKilometersPerHour(true);

                Angle splitAngle = Angle.fromAngle(splitAngleDegrees);

                // 11. 新しいレールを生成 (標準のAPIを使用)
                // ※ クライアント側で再計算されるため、カーブの膨らみを完全に防ぐことはできませんが、
                // ノードのオフセットが精密に設定されているため、接続点でのズレは最小限になります。
                Rail newRail1 = Rail.newRail(
                        pos1, angle1, splitPos, splitAngle,
                        shape, originalVerticalRadius, styles, speedLimit1, speedLimit2,
                        targetRail.isPlatform(), targetRail.isSiding(), targetRail.canAccelerate(),
                        targetRail.canConnectRemotely(), canHaveSignal, targetRail.getTransportMode()
                );

                Rail newRail2 = Rail.newRail(
                        splitPos, splitAngle, pos2, angle2,
                        shape, originalVerticalRadius, styles, speedLimit1, speedLimit2,
                        targetRail.isPlatform(), targetRail.isSiding(), targetRail.canAccelerate(),
                        targetRail.canConnectRemotely(), canHaveSignal, targetRail.getTransportMode()
                );

                rails.add(newRail1);
                rails.add(newRail2);

                // 12. クライアントへの再同期
                try {
                    Method syncMethod = simulator.getClass().getMethod("sync");
                    syncMethod.invoke(simulator);
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}