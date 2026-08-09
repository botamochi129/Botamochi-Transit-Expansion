package botamochi129.bte.mod.render;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.model.ModelSmallCube;
import org.mtr.mod.render.StoredMatrixTransformations;

public class StraightNodeBlockEntityRenderer extends BlockEntityRenderer<StraightNodeBlockEntity> {

    private static final Identifier TEXTURE_WOOD = new Identifier("minecraft", "textures/block/oak_log.png");
    private static final Identifier TEXTURE_METAL = new Identifier("mtr", "textures/block/metal.png");

    private static final ModelSmallCube CUBE_WOOD = new ModelSmallCube(TEXTURE_WOOD);
    private static final ModelSmallCube CUBE_METAL = new ModelSmallCube(TEXTURE_METAL);

    public StraightNodeBlockEntityRenderer(Argument argument) {
        super(argument);
    }

    @Override
    public void render(StraightNodeBlockEntity entity, float tickDelta, GraphicsHolder graphicsHolder, int light, int overlay) {
        final BlockPos pos = entity.getPos2();
        final double angleDeg = entity.getAngleDegrees();
        final boolean bound = entity.isBound();
        final boolean connected = entity.isConnected();

        float yawDegrees;
        if (bound) {
            // 【修正】内部角度(右回り)をMinecraftの回転(左回りが正)に変換し、モデルのデフォルト向きからのオフセット(-90)を適用
            yawDegrees = (float) (-angleDeg - 90.0);
        } else {
            // 【修正】未接続時のアニメーションも右回りに回転させるため、符号を反転させる
            long time = System.currentTimeMillis();
            float animAngle = (float) ((time % 36000L) / 10.0D);
            yawDegrees = -animAngle;
        }

        if (!connected) {
            renderNode(pos, yawDegrees, light);
        }
    }

    private void renderNode(BlockPos pos, float yawDegrees, int light) {
        // JSON model: element [from, to] (0~16)
        renderElement(pos, yawDegrees, light, CUBE_WOOD,  0, 0, 6,  16, 1, 10);
        renderElement(pos, yawDegrees, light, CUBE_METAL, 1, 1, 7,   2,12,  9);
        renderElement(pos, yawDegrees, light, CUBE_METAL,14, 1, 7,  15,12,  9);
        renderElement(pos, yawDegrees, light, CUBE_METAL, 0,12, 7.5, 16,16,8.5);
    }

    private void renderElement(BlockPos pos, float yawDegrees, int light, ModelSmallCube cube,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {

        // ブロックの中心 (X+0.5, Y+0.5, Z+0.5) を全体の基準原点にする
        StoredMatrixTransformations transforms = new StoredMatrixTransformations(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D
        );

        // ModelSmallCube は 8x8x8 サイズなので、目標サイズを 8.0 で割る
        float scaleX = (float) ((maxX - minX) / 8.0D);
        float scaleY = (float) ((maxY - minY) / 8.0D);
        float scaleZ = (float) ((maxZ - minZ) / 8.0D);

        // ブロック中心 (8, 8, 8) から見たパーツ中心の位置オフセット (16で割って0~1の空間へ)
        double offsetX = ((minX + maxX) / 2.0D - 8.0D) / 16.0D;
        double offsetY = ((minY + maxY) / 2.0D - 8.0D) / 16.0D;
        double offsetZ = ((minZ + maxZ) / 2.0D - 8.0D) / 16.0D;

        transforms.add(g -> {
            // 1. ブロック中心軸で回転
            g.rotateYDegrees(yawDegrees);

            // 2. パーツごとの中心位置へ移動
            g.translate(offsetX, offsetY, offsetZ);

            // 3. パーツの大きさにスケール
            g.scale(scaleX, scaleY, scaleZ);

            // 4. ModelSmallCube の元々の中心 (0.5, 0.5, 0.5) を原点に移動してスケールを効かせる
            g.translate(-0.5D, -0.5D, -0.5D);
        });

        cube.render(transforms, light);
    }
}