package botamochi129.bte.mod.registry;

import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.RenderLayer;
import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.registry.BlockEntityTypeRegistryObject;
import org.mtr.mapping.registry.BlockRegistryObject;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.registry.RegistryClient;

import java.util.function.Function;

public class BTERegistryClient {
    public static final RegistryClient REGISTRY_CLIENT = new RegistryClient(BTERegistry.REGISTRY);

    public static void setupPackets(Identifier id) {
        REGISTRY_CLIENT.setupPackets(id);
    }

    public static <T extends PacketHandler> void sendPacketToServer(T packet) {
        REGISTRY_CLIENT.sendPacketToServer(packet);
    }

    public static <T extends BlockEntityTypeRegistryObject<U>, U extends BlockEntityExtension> void registerBlockEntityRenderer(T type, Function<BlockEntityRenderer.Argument, BlockEntityRenderer<U>> factory) {
        REGISTRY_CLIENT.registerBlockEntityRenderer(type, factory);
    }

    public static void registerBlockRenderType(RenderLayer layer, BlockRegistryObject block) {
        REGISTRY_CLIENT.registerBlockRenderType(layer, block);
    }

    public static void init() {
        REGISTRY_CLIENT.init();
    }
}
