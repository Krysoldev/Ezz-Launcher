package io.ezz.skinmod.common;

import java.io.File;

public class EzzSkinModCommon {
    public static final String MOD_ID = "ezzskin";
    public static final String MOD_NAME = "Ezz Skin Fabric Mod";
    public static final String VERSION = "1.0.0";

    private static EzzSkinConfig config = new EzzSkinConfig();
    private static boolean initialized = false;

    public static void init(File runDirectory) {
        if (initialized) return;

        File configFile = new File(runDirectory, "config/ezz-skin-config.json");
        config = EzzSkinConfig.load(configFile);
        initialized = true;

        if (config.enabled) {
            System.out.println("[EzzSkinMod] Initialized with account: " + config.accountId + 
                ", skinHash: " + config.skinHash + ", model: " + config.model);
        } else {
            System.out.println("[EzzSkinMod] Initialized (disabled / vanilla fallback).");
        }
    }

    public static EzzSkinConfig getConfig() {
        return config;
    }

    public static boolean isAlexModel() {
        return "ALEX".equalsIgnoreCase(config.model) || "SLIM".equalsIgnoreCase(config.model);
    }
}
