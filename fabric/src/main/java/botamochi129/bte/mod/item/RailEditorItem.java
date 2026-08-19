package botamochi129.bte.mod.item;

import botamochi129.bte.mod.packet.PacketSplitRail;
import botamochi129.bte.mod.registry.BTERegistryClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.Hand;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.ItemExtension;
import org.mtr.mod.client.MinecraftClientData;

public class RailEditorItem extends ItemExtension {

    private static final double REACH_DISTANCE = 64.0;
    private static final double SELECTION_RADIUS = 1.5;

    public RailEditorItem(ItemSettings settings) {
        super(settings.maxCount(1));
    }

    @Override
    public void useWithoutResult(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient()) return;

        MinecraftClientData clientData = MinecraftClientData.getInstance();
        if (clientData == null) return;

        net.minecraft.entity.player.PlayerEntity nativePlayer =
                (net.minecraft.entity.player.PlayerEntity) user.data;

        Vec3d eyePos = nativePlayer.getEyePos();
        Vec3d lookVec = nativePlayer.getRotationVec(1.0f);

        ItemStack stack = user.getStackInHand(hand);
        CompoundTag nbt = stack.getOrCreateTag();
        String selectedRailId = nbt.contains("SelectedRail") ? nbt.getString("SelectedRail") : null;

        Rail closestRail = null;
        double minDistanceSq = SELECTION_RADIUS * SELECTION_RADIUS;
        double minRayProgress = Double.MAX_VALUE;
        double closestDistanceOnRail = 0;

        for (Rail rail : clientData.rails) {
            if (!rail.isValid()) continue;

            double length = rail.railMath.getLength();
            // ★ 修正: サンプリング間隔を細かくし、どの位置でも分割できるようにする
            int steps = Math.max(20, (int) (length / 0.25));

            double railClosestDistSq = Double.MAX_VALUE;
            double railClosestT = Double.MAX_VALUE;
            double railClosestProgress = 0;

            for (int i = 0; i <= steps; i++) {
                double dist = (length * i) / steps;
                Vector point = rail.railMath.getPosition(dist, false);

                double dx = point.x() - eyePos.x;
                double dy = point.y() - eyePos.y;
                double dz = point.z() - eyePos.z;

                double t = dx * lookVec.x + dy * lookVec.y + dz * lookVec.z;

                if (t > 0 && t <= REACH_DISTANCE) {
                    double closestX = eyePos.x + lookVec.x * t;
                    double closestY = eyePos.y + lookVec.y * t;
                    double closestZ = eyePos.z + lookVec.z * t;

                    double distX = point.x() - closestX;
                    double distY = point.y() - closestY;
                    double distZ = point.z() - closestZ;
                    double distSq = distX * distX + distY * distY + distZ * distZ;

                    if (distSq < railClosestDistSq) {
                        railClosestDistSq = distSq;
                        railClosestT = t;
                        railClosestProgress = dist;
                    }
                }
            }

            if (railClosestDistSq < minDistanceSq && railClosestT < minRayProgress) {
                minDistanceSq = railClosestDistSq;
                minRayProgress = railClosestT;
                closestRail = rail;
                closestDistanceOnRail = railClosestProgress;
            }
        }

        if (closestRail != null) {
            // ★ 追加: レール選択中かつ視線がレール上にある場合、分割位置をプレビュー
            if (selectedRailId != null && selectedRailId.equals(closestRail.getHexId())) {

                // プレビュー用パーティクル (分割位置に集中表示)
                net.minecraft.client.world.ClientWorld clientWorld =
                        (net.minecraft.client.world.ClientWorld) world.data;
                Vector splitPoint = closestRail.railMath.getPosition(closestDistanceOnRail, false);

                // 分割位置に目立つパーティクルを出す
                for (int i = 0; i < 10; i++) {
                    clientWorld.addParticle(ParticleTypes.WAX_ON,
                            splitPoint.x() + (Math.random() - 0.5) * 0.2,
                            splitPoint.y() + 0.5 + (Math.random() - 0.5) * 0.2,
                            splitPoint.z() + (Math.random() - 0.5) * 0.2,
                            0, 0.1, 0);
                }

                // 始点・終点の境界チェック (端から0.5ブロック以内は分割不可)
                if (closestDistanceOnRail > 0.5 && closestDistanceOnRail < closestRail.railMath.getLength() - 0.5) {

                    // パケット送信 (分割実行)
                    BTERegistryClient.sendPacketToServer(new PacketSplitRail(selectedRailId, closestDistanceOnRail));

                    nbt.remove("SelectedRail");
                    nativePlayer.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f);
                } else {
                    nativePlayer.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
                }
            }
            // 選択中ではない -> 選択モード
            else {
                nbt.putString("SelectedRail", closestRail.getHexId());
                nativePlayer.playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f, 1.5f);

                // レール全体の軌道をパーティクルで表示
                net.minecraft.client.world.ClientWorld clientWorld =
                        (net.minecraft.client.world.ClientWorld) world.data;
                int steps = Math.max(20, (int) (closestRail.railMath.getLength() / 1.0));
                for (int i = 0; i <= steps; i++) {
                    double dist = (closestRail.railMath.getLength() * i) / steps;
                    Vector mid = closestRail.railMath.getPosition(dist, false);
                    clientWorld.addParticle(ParticleTypes.END_ROD, mid.x(), mid.y() + 0.5, mid.z(), 0, 0, 0);
                }
            }
        } else {
            // 何も見つからなければ選択解除
            if (selectedRailId != null) {
                nbt.remove("SelectedRail");
                nativePlayer.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f);
            }
        }
    }
}