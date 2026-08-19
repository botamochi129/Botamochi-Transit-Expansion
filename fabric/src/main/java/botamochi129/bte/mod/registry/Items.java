package botamochi129.bte.mod.registry;

import botamochi129.bte.mod.item.RailEditorItem;
import org.mtr.mapping.holder.Item; // ★追加
import org.mtr.mapping.registry.ItemRegistryObject;

public class Items {

    public static final ItemRegistryObject RAIL_EDITOR = BTERegistry.registerItem(
            "rail_editor",
            settings -> new Item(new RailEditorItem(settings)), // ★ここでラップする
            ItemGroups.MAIN
    );

    public static void register() {
    }
}