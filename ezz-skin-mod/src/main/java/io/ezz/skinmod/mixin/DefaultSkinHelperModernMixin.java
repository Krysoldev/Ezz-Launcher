package io.ezz.skinmod.mixin;

import com.mojang.authlib.GameProfile;
import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

/**
 * Modern Mixin for DefaultSkinHelper (net.minecraft.class_1068) in Minecraft 1.20.2+, 1.21.x, and 1.26.x.
 * Intercepts default skin fallbacks to supply the local player's Vault skin.
 */
@Mixin(targets = "net.minecraft.class_1068", remap = false)
public class DefaultSkinHelperModernMixin {

    @Inject(method = {"getSkinTextures(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/class_8685;", "method_52854(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/class_8685;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezz_getSkinTexturesFromProfile(GameProfile profile, CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(profile)) {
            EzzSkinTextureProvider.updateServerSkinState(profile);
            if (!EzzSkinTextureProvider.hasServerSkinOverride()) {
                Object custom = EzzSkinTextureProvider.getCustomSkinTextures(profile);
                if (custom != null) {
                    cir.setReturnValue(custom);
                }
            }
        }
    }

    @Inject(method = {"getSkinTextures(Ljava/util/UUID;)Lnet/minecraft/class_8685;", "method_4648(Ljava/util/UUID;)Lnet/minecraft/class_8685;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezz_getSkinTexturesFromUuid(UUID uuid, CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(uuid)) {
            if (!EzzSkinTextureProvider.hasServerSkinOverride()) {
                Object custom = EzzSkinTextureProvider.getCustomSkinTextures(uuid);
                if (custom != null) {
                    cir.setReturnValue(custom);
                }
            }
        }
    }
}
