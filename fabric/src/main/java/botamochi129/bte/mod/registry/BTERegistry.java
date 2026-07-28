package botamochi129.bte.mod.registry;

import botamochi129.bte.mod.Constants;
import org.mtr.mapping.holder.Block;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.Item;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockItemExtension;
import org.mtr.mapping.registry.BlockEntityTypeRegistryObject;
import org.mtr.mapping.registry.BlockRegistryObject;
import org.mtr.mapping.registry.CreativeModeTabHolder;
import org.mtr.mapping.registry.ItemRegistryObject;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.registry.Registry;
import org.mtr.mapping.tool.PacketBufferReceiver;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class BTERegistry {
    public static final Registry REGISTRY = new Registry();

    public static void setupPackets(Identifier id) {
        REGISTRY.setupPackets(id);
    }

    public static <T extends PacketHandler> void registerPacket(Class<T> classObject, Function<PacketBufferReceiver, T> getInstance) {
        REGISTRY.registerPacket(classObject, getInstance);
    }

    public static BlockRegistryObject registerBlockWithBlockItem(String id, Supplier<Block> supplier, CreativeModeTabHolder... itemGroup) {
        return REGISTRY.registerBlockWithBlockItem(new Identifier(Constants.MOD_ID, id), supplier, itemGroup);
    }

    public static BlockRegistryObject registerBlockWithBlockItem(String id, Supplier<Block> supplier, BiFunction<Block, ItemSettings, BlockItemExtension> blockItemFactory, CreativeModeTabHolder... itemGroup) {
        return REGISTRY.registerBlockWithBlockItem(new Identifier(Constants.MOD_ID, id), supplier, blockItemFactory, itemGroup);
    }

    public static BlockRegistryObject registerBlock(String id, Supplier<Block> supplier) {
        return REGISTRY.registerBlock(new Identifier(Constants.MOD_ID, id), supplier);
    }

    public static ItemRegistryObject registerItem(String id, Function<ItemSettings, Item> callback, CreativeModeTabHolder... itemGroup) {
        return REGISTRY.registerItem(new Identifier(Constants.MOD_ID, id), callback, itemGroup);
    }

    public static <T extends BlockEntityExtension> BlockEntityTypeRegistryObject<T> registerBlockEntityType(String id, BiFunction<BlockPos, BlockState, T> constructor, Supplier<Block>... blocks) {
        return REGISTRY.registerBlockEntityType(new Identifier(Constants.MOD_ID, id), constructor, blocks);
    }

    public static CreativeModeTabHolder createCreativeModeTabHolder(Identifier id, Supplier<ItemStack> iconSupplier) {
        return REGISTRY.createCreativeModeTabHolder(id, iconSupplier);
    }
}
