package io.ezz.skinmod.mixin;

import com.mojang.authlib.GameProfile;
import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Mixin(targets = "net.minecraft.class_1068", remap = false)
public class DefaultSkinHelperMixin {

    // 1.20.2+ / 1.21+ (GameProfile) -> SkinTextures (method_52854)
    @Inject(method = {"getSkinTextures(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/class_8685;", "method_52854(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/class_8685;", "method_52854"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezz_getSkinTexturesFromProfile(GameProfile profile, CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(profile)) {
            Object custom = EzzSkinTextureProvider.getCustomSkinTextures(profile);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    // 1.20.2+ / 1.21+ (UUID) -> SkinTextures (method_4648)
    @Inject(method = {"getSkinTextures(Ljava/util/UUID;)Lnet/minecraft/class_8685;", "method_4648(Ljava/util/UUID;)Lnet/minecraft/class_8685;", "method_4648"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezz_getSkinTexturesFromUuid(UUID uuid, CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(uuid)) {
            Object custom = EzzSkinTextureProvider.getCustomSkinTextures(uuid);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    // 1.16 - 1.20.1 (UUID) -> Identifier (method_4648)
    @Inject(method = {"getTexture(Ljava/util/UUID;)Lnet/minecraft/class_2960;", "method_4648(Ljava/util/UUID;)Lnet/minecraft/class_2960;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezz_getTextureFromUuid(UUID uuid, CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(uuid)) {
            Object custom = EzzSkinTextureProvider.getCustomSkinTexture(uuid);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    // 1.16 - 1.20.1 (UUID) -> String (method_4649)
    @Inject(method = {"getModel(Ljava/util/UUID;)Ljava/lang/String;", "method_4649(Ljava/util/UUID;)Ljava/lang/String;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezz_getModelFromUuid(UUID uuid, CallbackInfoReturnable<String> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(uuid)) {
            String model = EzzSkinTextureProvider.getCustomModel(uuid);
            if (model != null) {
                cir.setReturnValue(model);
            }
        }
    }
}
