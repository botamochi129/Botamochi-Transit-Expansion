package botamochi129.bte.mod.registry;

import botamochi129.bte.mod.block.StraightNodeBlock;
import botamochi129.bte.mod.block.entity.StraightNodeBlockEntity;
import botamochi129.bte.mod.item.StraightNodeItem; // ← 追加
import org.mtr.mapping.holder.Block;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.mapper.BlockItemExtension;
import org.mtr.mapping.registry.BlockEntityTypeRegistryObject;
import org.mtr.mapping.registry.BlockRegistryObject;

import java.util.function.Supplier;

public class Blocks {

    // 【修正】カスタムファクトリを使用して StraightNodeItem を登録する
    public static final BlockRegistryObject STRAIGHT_NODE = BTERegistry.registerBlockWithBlockItem(
            "straight_node",
            () -> new Block(new StraightNodeBlock()),
            StraightNodeItem::new, // ← ここを追加
            ItemGroups.MAIN
    );

    public static final BlockEntityTypeRegistryObject<StraightNodeBlockEntity> STRAIGHT_NODE_BE =
            BTERegistry.registerBlockEntityType(
                    "straight_node",
                    StraightNodeBlockEntity::new,
                    (Supplier<Block>) STRAIGHT_NODE::get
            );

    public static void register() {
    }
}