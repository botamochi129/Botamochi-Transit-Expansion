package botamochi129.bte.entrypoint;

import botamochi129.bte.mod.BTEClient;
import net.fabricmc.api.ClientModInitializer;

public class MainClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BTEClient.initialize();
    }
}