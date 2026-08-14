package botamochi129.bte.mod.item;

import org.mtr.mapping.holder.Block;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.BlockItemExtension;
import org.mtr.mapping.mapper.TextHelper;

import java.util.ArrayList;
import java.util.List;

public class StraightNodeItem extends BlockItemExtension {

    private static final double UNBOUND_SENTINEL = -129129.0;
    // ★ 修正: 角度とオフセットの両方を包含する新しいフォーマットキー
    // 言語ファイル側で "%s (%s)" のように定義してください
    private static final String FORMAT_KEY = "item.bte.straight_node.format";

    public StraightNodeItem(Block block, ItemSettings settings) {
        super(block, settings);
    }

    @Override
    public Text getName2(ItemStack stack) {
        String baseName = super.getName2(stack).getString();
        CompoundTag tag = stack.getTag();

        if (tag != null && tag.contains("BlockEntityTag")) {
            CompoundTag beTag = tag.getCompound("BlockEntityTag");
            StringBuilder info = new StringBuilder();

            // 1. 角度の処理
            if (beTag.contains("angle_degrees")) {
                double angle = beTag.getDouble("angle_degrees");
                if (angle != UNBOUND_SENTINEL) {
                    // 180°周期で正規化 (軸としての表示)
                    double displayAngle = angle % 180.0;
                    if (displayAngle < 0.0) displayAngle += 180.0;
                    info.append(String.format("%.1f°", displayAngle));
                }
            }

            // 2. オフセットの処理
            double offX = beTag.contains("offset_x") ? beTag.getDouble("offset_x") : 0.0;
            double offY = beTag.contains("offset_y") ? beTag.getDouble("offset_y") : 0.0;
            double offZ = beTag.contains("offset_z") ? beTag.getDouble("offset_z") : 0.0;

            List<String> offsetParts = new ArrayList<>();
            // 0.001未満の誤差は無視し、0でないものだけ表示する
            if (Math.abs(offX) > 0.001) offsetParts.add(String.format("X:%.2f", offX));
            if (Math.abs(offY) > 0.001) offsetParts.add(String.format("Y:%.2f", offY));
            if (Math.abs(offZ) > 0.001) offsetParts.add(String.format("Z:%.2f", offZ));

            if (!offsetParts.isEmpty()) {
                if (info.length() > 0) info.append(", ");
                info.append(String.join(", ", offsetParts));
            }

            // 3. 表示名の生成
            if (info.length() > 0) {
                // 例: "Straight Node (45.0°, X:0.50, Z:-0.20)"
                return Text.cast(TextHelper.translatable(FORMAT_KEY, baseName, info.toString()));
            }
        }

        return super.getName2(stack);
    }
}