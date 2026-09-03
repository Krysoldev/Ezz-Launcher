package io.ezz.skinmod.v1_16;

import io.ezz.skinmod.common.EzzSkinModCommon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class EzzSkinMod116 implements ClientModInitializer {

    private static Identifier customSkinIdentifier = null;
    private static boolean textureLoaded = false;

    @Override
    public void onInitializeClient() {
        File gameDir = FabricLoader.getInstance().getGameDir().toFile();
        EzzSkinModCommon.init(gameDir);
    }

    public static Identifier getCustomSkinIdentifier() {
        if (!textureLoaded) {
            loadTexture();
        }
        return customSkinIdentifier;
    }

    private static synchronized void loadTexture() {
        if (textureLoaded) return;
        textureLoaded = true;

        if (!EzzSkinModCommon.getConfig().enabled) {
            return;
        }

        File gameDir = FabricLoader.getInstance().getGameDir().toFile();
        File skinFile = new File(gameDir, EzzSkinModCommon.getConfig().skinFile);

        if (!skinFile.exists()) {
            System.err.println("[EzzSkinMod-1.16] Skin file not found at " + skinFile.getAbsolutePath());
            return;
        }

        try (InputStream is = new FileInputStream(skinFile)) {
            NativeImage image = NativeImage.read(is);
            NativeImageBackedTexture dynamicTexture = new NativeImageBackedTexture(image);
            String hash = EzzSkinModCommon.getConfig().skinHash;
            String safeHash = (hash != null && !hash.isEmpty()) ? hash : "default";
            Identifier id = new Identifier("ezzskin", "textures/skin/" + safeHash);

            MinecraftClient.getInstance().getTextureManager().registerTexture(id, dynamicTexture);
            customSkinIdentifier = id;
            System.out.println("[EzzSkinMod-1.16] Successfully registered custom skin texture: " + id);
        } catch (Exception e) {
            System.err.println("[EzzSkinMod-1.16] Error registering skin texture: " + e.getMessage());
        }
    }
}
