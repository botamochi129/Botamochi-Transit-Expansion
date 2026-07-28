package botamochi129.bte.mod.render;

import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.model.ModelSmallCube;
import org.mtr.mod.render.StoredMatrixTransformations;

public class StraightNodeBlockEntityRenderer extends BlockEntityRenderer<StraightNodeBlockEntity> {

    private static final ModelSmallCube MODEL = new ModelSmallCube(new Identifier("bte", "textures/block/straight_node.png"));

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
        if (bound && connected) {
            yawDegrees = (float) (90 - angleDeg);
        } else if (bound) {
            yawDegrees = (float) -angleDeg;
        } else {
            yawDegrees = (float) ((System.currentTimeMillis() / 1000.0 * 360) % 360);
        }

        StoredMatrixTransformations transforms = new StoredMatrixTransformations(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5
        );
        transforms.add(graphicsHolder1 -> {
            graphicsHolder1.translate(0, 0.5, 0);
            graphicsHolder1.rotateYDegrees(yawDegrees);
        });
        MODEL.render(transforms, light);
    }
}