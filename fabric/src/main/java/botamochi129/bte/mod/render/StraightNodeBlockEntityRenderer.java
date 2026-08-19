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

        final double offX = entity.getOffsetX();
        final double offY = entity.getOffsetY();
        final double offZ = entity.getOffsetZ();

        // ★ 追加: カント角の取得
        final double rollDeg = entity.getRollDegrees();

        float yawDegrees;
        if (bound) {
            yawDegrees = (float) (-angleDeg - 90.0);
        } else {
            long time = System.currentTimeMillis();
            float animAngle = (float) ((time % 36000L) / 10.0D);
            yawDegrees = -animAngle;
        }

        if (!connected) {
            // ★ 修正: rollDeg を渡す
            renderNode(pos, yawDegrees, (float) rollDeg, light, offX, offY, offZ);
        }
    }

    private void renderNode(BlockPos pos, float yawDegrees, float rollDegrees, int light, double offX, double offY, double offZ) {
        renderElement(pos, yawDegrees, rollDegrees, light, CUBE_WOOD,  0, 0, 6,  16, 1, 10, offX, offY, offZ);
        renderElement(pos, yawDegrees, rollDegrees, light, CUBE_METAL, 1, 1, 7,   2,12,  9, offX, offY, offZ);
        renderElement(pos, yawDegrees, rollDegrees, light, CUBE_METAL,14, 1, 7,  15,12,  9, offX, offY, offZ);
        renderElement(pos, yawDegrees, rollDegrees, light, CUBE_METAL, 0,12, 7.5, 16,16,8.5, offX, offY, offZ);
    }

    private void renderElement(BlockPos pos, float yawDegrees, float rollDegrees, int light, ModelSmallCube cube,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ,
                               double offX, double offY, double offZ) {

        StoredMatrixTransformations transforms = new StoredMatrixTransformations(
                pos.getX() + 0.5D + offX, pos.getY() + 0.5D + offY, pos.getZ() + 0.5D + offZ
        );

        float scaleX = (float) ((maxX - minX) / 8.0D);
        float scaleY = (float) ((maxY - minY) / 8.0D);
        float scaleZ = (float) ((maxZ - minZ) / 8.0D);

        double offsetX = ((minX + maxX) / 2.0D - 8.0D) / 16.0D;
        double offsetY = ((minY + maxY) / 2.0D - 8.0D) / 16.0D;
        double offsetZ = ((minZ + maxZ) / 2.0D - 8.0D) / 16.0D;

        transforms.add(g -> {
            g.rotateYDegrees(yawDegrees);
            // ★ 追加: カント（ロール）の適用
            // StraightNodeBlock のモデルはX軸方向に長いため、X軸回転でバンクを表現します
            g.rotateZDegrees(rollDegrees);

            g.translate(offsetX, offsetY, offsetZ);
            g.scale(scaleX, scaleY, scaleZ);
            g.translate(-0.5D, -0.5D, -0.5D);
        });

        cube.render(transforms, light);
    }
}