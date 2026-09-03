package io.ezz.skinmod;

import io.ezz.skinmod.common.EzzSkinConfig;
import io.ezz.skinmod.common.EzzSkinModCommon;
import io.ezz.skinmod.common.EzzSkinTextureProvider;
import net.fabricmc.api.ClientModInitializer;
import java.io.File;

public class EzzSkinMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("==================================================");
        System.out.println("[EZZ-SKIN] CHECKPOINT 1 — Mod initialization");
        EzzSkinModCommon.init(new File("."));

        EzzSkinConfig config = EzzSkinModCommon.getConfig();
        System.out.println("[EZZ-SKIN] CHECKPOINT 2 — Config loaded");
        System.out.println("[EZZ-SKIN] CHECKPOINT 3 — Account loaded: " + (config.username.isEmpty() ? config.accountId : config.username));
        System.out.println("[EZZ-SKIN] CHECKPOINT 4 — Skin metadata loaded (ID: " + config.skinId + ", Model: " + config.model + ")");
        System.out.println("[EZZ-SKIN] Account UUID = " + config.uuid);
        System.out.println("[EZZ-SKIN] Skin file = " + config.skinFile);
        System.out.println("==================================================");

        EzzSkinTextureProvider.ensureTextureLoaded();
    }
}
