package botamochi129.bte.mod;

import org.mtr.mapping.holder.Identifier;

public class Constants {
    public static final String MOD_NAME = "Botamochi Transit Expansion";
    public static final String MOD_ID = "bte";
    public static final String LOGGING_PREFIX = "[BTE] ";
    public static final int MC_TICK_PER_SECOND = 20;

    public static Identifier id(String id) {
        return new Identifier(MOD_ID, id);
    }
}
