package io.ezz.skinmod.v1_21;

import io.ezz.skinmod.common.EzzSkinModCommon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class EzzSkinMod121 implements ClientModInitializer {

    private static Identifier customSkinIdentifier = null;
    private static SkinTextures customSkinTextures = null;
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

    public static SkinTextures getCustomSkinTextures() {
        if (!textureLoaded) {
            loadTexture();
        }
        return customSkinTextures;
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
            System.err.println("[EzzSkinMod-1.21] Skin file not found at " + skinFile.getAbsolutePath());
            return;
        }

        try (InputStream is = new FileInputStream(skinFile)) {
            NativeImage image = NativeImage.read(is);
            NativeImageBackedTexture dynamicTexture = new NativeImageBackedTexture(image);
            String hash = EzzSkinModCommon.getConfig().skinHash;
            String safeHash = (hash != null && !hash.isEmpty()) ? hash : "default";
            Identifier id = Identifier.of("ezzskin", "textures/skin/" + safeHash);

            MinecraftClient.getInstance().getTextureManager().registerTexture(id, dynamicTexture);
            customSkinIdentifier = id;

            SkinTextures.Model model = EzzSkinModCommon.isAlexModel() ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE;
            customSkinTextures = new SkinTextures(id, null, null, null, model, true);

            System.out.println("[EzzSkinMod-1.21] Successfully registered custom skin texture: " + id + " (" + model + ")");
        } catch (Exception e) {
            System.err.println("[EzzSkinMod-1.21] Error registering skin texture: " + e.getMessage());
        }
    }
}
