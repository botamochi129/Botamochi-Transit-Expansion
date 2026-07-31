package botamochi129.bte.mod.item;

import org.mtr.mapping.holder.Block;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.BlockItemExtension;
import org.mtr.mapping.mapper.TextHelper;

public class StraightNodeItem extends BlockItemExtension {

    private static final double UNBOUND_SENTINEL = -129129.0;
    private static final String ANGLE_FORMAT_KEY = "item.bte.straight_node.angle_format";

    public StraightNodeItem(Block block, ItemSettings settings) {
        super(block, settings);
    }

    @Override
    public Text getName2(ItemStack stack) {
        String baseNameString = super.getName2(stack).getString();

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("BlockEntityTag")) {
            CompoundTag beTag = tag.getCompound("BlockEntityTag");

            if (beTag.contains("angle_degrees")) {
                double angle = beTag.getDouble("angle_degrees");

                if (angle != UNBOUND_SENTINEL) {
                    // 【確定修正】180°周期で正規化する (MTRノードの仕様と同じ)
                    double displayAngle = angle % 180.0;
                    if (displayAngle < 0.0) {
                        displayAngle += 180.0;
                    }

                    return Text.cast(TextHelper.translatable(ANGLE_FORMAT_KEY, baseNameString, displayAngle));
                }
            }
        }

        return super.getName2(stack);
    }
}