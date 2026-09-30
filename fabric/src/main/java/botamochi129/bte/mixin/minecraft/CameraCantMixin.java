package botamochi129.bte.mixin.minecraft;

import botamochi129.bte.mod.rail.RailCantDirectionTracker;
import botamochi129.bte.mod.rail.RailCantRidingState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
#if LOADER == "fabric"
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
#else
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
#endif

/**
 * 乗車中のカメラに、走行中のレールのカント角をロールとして与える。
 *
 * <p>1.20.1 の {@code GameRenderer} はワールド描画のビュー行列を
 * {@code Camera#getRotation()} から組み立てない。{@code renderWorld} でカメラの pitch /
 * yaw を {@code MatrixStack.multiply(Quaternionf)} に直接積んでいるため、従来の
 * {@code Camera#getRotation()} への注入では計算は走るが画面には一切反映されなかった。
 *
 * <p>そこで {@code renderWorld} 内の yaw 回転（カメラ回転の最後のステップ）の直後に
 * 回転を追加する。同箇所は camera-roll 系 Mod が 1.20.x で共通して狙う位置である。
 *
 * <p>この回転の軸を「ワールド Z 軸」にすると、カメラの yaw が Z 軸から外れる
 * （線路が横を向く、pitch が付く）ほどロール軸が視線からズレて見える。正しく画面の
 * 傾きとして出すには、カメラの視線方向そのものを回転軸にする必要がある。
 *
 * <p>また回転の中心は「プレイヤーの足元」にする。回転が視線位置（目の高さ）を通る軸だと
 * ワールドはその場で回るだけで横へ動かず、実物の身体がバンクで足元を支点に傾くような
 * 微妙な視点の横ずれが出ない。そこで足元を回転中心にする。
 *
 * <p>回転中心はワールドの絶対座標（何千ブロック先の 0,0 寄り座標）で扱ってはならない。
 * {@code F - R0·F} の平行移動は原点から遠いほど巨大になり、ワールドが原点方向へ
 * 数百ブロック動く事故になる。必ず「カメラ位置を原点としたローカル座標」{@code P = 足元 - カメラ} で
 * 回転中心を表し、{@code T(P)·R0·T(-P)} と合成する。これで P は高々数ブロックの大きさに
 * 収まり、足元が回転中心になる（= 視点が微妙に横へ振れる）。
 *
 * <p>車両等の 3D 描画は {@link botamochi129.bte.mixin.mtr.RenderVehiclesCantMixin} 側が
 * 担当する。車両に乗車中のみ適用する（未乗車時はワールドを傾けない）。
 */
@Mixin(GameRenderer.class)
public abstract class CameraCantMixin {

    /**
     * {@code renderWorld}/{@code renderLevel} で yaw 回転を積む
     * {@code MatrixStack.multiply}/{@code PoseStack.mulPose} 呼び出しの ordinal。
     * バイトコード順でカメラ回転の直前に存在する multiply(Quaternionf) は
     * （nausea 2 回 + pitch 1 回）の計 3 回なので、yaw は ordinal = 3 になる。
     */
    @Unique
    private static final int YAW_ROTATION_ORDINAL = 3;

    #if LOADER == "fabric"
    @Redirect(
            method = "renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V",
                    ordinal = YAW_ROTATION_ORDINAL
            ),
            require = 1
    )
    private void bte$applyCameraRoll(MatrixStack stack, Quaternionf yawQuat) {
        #else
    @Redirect(
            method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
                    ordinal = YAW_ROTATION_ORDINAL
            ),
            require = 1
    )
    private void bte$applyCameraRoll(PoseStack stack, Quaternionf yawQuat) {
        #endif
        #if LOADER == "fabric"
        stack.multiply(yawQuat);
        #else
        stack.mulPose(yawQuat);
        #endif

        final float roll = bte$currentCameraRoll();
        if (roll == 0.0F) {
            return;
        }

        bte$applyRoll(stack, roll);
    }

    @Unique
    #if LOADER == "fabric"
    private void bte$applyRoll(MatrixStack stack, float roll) {
        #else
    private void bte$applyRoll(PoseStack stack, float roll) {
        #endif
        final float[] axis = bte$cameraViewAxis();
        if (axis == null) {
            return;
        }
        final float[] feet = bte$cameraFeetPosition();
        final float[] cameraPos = bte$cameraPosition();
        if (feet == null || cameraPos == null) {
            return;
        }

        // 回転中心はカメラ位置からのローカル座標で持つ（絶対座標で持つと 0,0 から遠いほど
        // 平行移動が巨大になりワールドが飛ぶ）。P = 足元 - カメラ位置。
        final float pivotX = feet[0] - cameraPos[0];
        final float pivotY = feet[1] - cameraPos[1];
        final float pivotZ = feet[2] - cameraPos[2];

        // P + R0(p - P) = T(P)·R0·T(-P) を積む。右端が先に頂点に掛かる。
        // 符号は「車両の視覚的な傾きと逆に世界が傾く」期待で -roll を採用している。
        // 実機で逆に見えた場合はこのマイナスを外す。
        final Quaternionf rotate = new Quaternionf().rotationAxis((float) Math.toRadians(-roll), axis[0], axis[1], axis[2]);
        stack.translate(pivotX, pivotY, pivotZ);
        #if LOADER == "fabric"
        stack.multiply(rotate);
        #else
        stack.mulPose(rotate);
        #endif
        stack.translate(-pivotX, -pivotY, -pivotZ);
    }

    @Unique
    private float[] bte$cameraPosition() {
        #if LOADER == "fabric"
        final var client = net.minecraft.client.MinecraftClient.getInstance();
        #else
        final var client = net.minecraft.client.Minecraft.getInstance();
        #endif
        if (client == null) {
            return null;
        }

        #if LOADER == "fabric"
        final var camera = client.gameRenderer.getCamera();
        final Vec3d pos = camera.getPos();
        return new float[]{(float) pos.getX(), (float) pos.getY(), (float) pos.getZ()};
        #else
        final var camera = client.gameRenderer.getMainCamera();
        final Vec3 pos = camera.getPosition();
        return new float[]{(float) pos.x, (float) pos.y, (float) pos.z};
        #endif
    }

    @Unique
    private float[] bte$cameraFeetPosition() {
        #if LOADER == "fabric"
        final var client = net.minecraft.client.MinecraftClient.getInstance();
        #else
        final var client = net.minecraft.client.Minecraft.getInstance();
        #endif
        if (client == null) {
            return null;
        }

        final var cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) {
            return null;
        }

        return new float[]{(float) cameraEntity.getX(), (float) cameraEntity.getY(), (float) cameraEntity.getZ()};
    }

    @Unique
    private float[] bte$cameraViewAxis() {
        #if LOADER == "fabric"
        final var client = net.minecraft.client.MinecraftClient.getInstance();
        #else
        final var client = net.minecraft.client.Minecraft.getInstance();
        #endif
        if (client == null) {
            return null;
        }

        final var cameraEntity = client.getCameraEntity();
        #if LOADER == "fabric"
        if (cameraEntity == null) {
            return null;
        }
        final Vec3d view = cameraEntity.getRotationVec(1.0F);
        return new float[]{(float) view.getX(), (float) view.getY(), (float) view.getZ()};
        #else
        if (cameraEntity == null) {
            return null;
        }
        final Vec3 view = cameraEntity.getViewVector(1.0F);
        return new float[]{(float) view.x, (float) view.y, (float) view.z};
        #endif
    }

    @Unique
    private float bte$currentCameraRoll() {
        #if LOADER == "fabric"
        final var client = net.minecraft.client.MinecraftClient.getInstance();
        #else
        final var client = net.minecraft.client.Minecraft.getInstance();
        #endif
        if (client == null) {
            return 0.0F;
        }

        final var cameraEntity = client.getCameraEntity();
        if (cameraEntity == null) {
            return 0.0F;
        }

        // ★ 車両に乗車中のみ適用する
        // MTR は vanilla の搭乗機構を使わず独自状態（RenderVehicles.RIDING_PLAYER_INTERPOLATIONS）で
        // 乗車を管理するため、getVehicle() は乗車中も常に null になる。ここでは MTR 側の状態を参照する。
        #if LOADER == "fabric"
        if (!RailCantRidingState.isRiding(cameraEntity.getUuid())) {
            return 0.0F;
        }
        #else
        if (!RailCantRidingState.isRiding(cameraEntity.getUUID())) {
            return 0.0F;
        }
        #endif

        // カメラは vanilla エンティティの絶対座標が使えるため、そのまま tracker へ渡す。
        return RailCantDirectionTracker.cantFor(
                RailCantDirectionTracker.STREAM_CAMERA,
                cameraEntity.getX(),
                cameraEntity.getY(),
                cameraEntity.getZ()
        ).cant;
    }
}