package io.ezz.skinmod.mixin;

import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Universal Mixin for AbstractClientPlayerEntity (net.minecraft.class_742).
 * Strictly specifies exact method descriptors so that Mixin never matches wrong-return-type methods
 * (such as method_3118 which is getFovMultiplier returning float).
 */
@Mixin(targets = "net.minecraft.class_742", remap = false)
public class AbstractClientPlayerEntityMixin {

    // 1.20.2+ / 1.21+ / 1.26+ SkinTextures getSkinTextures() (method_52814)
    @Inject(method = {"getSkinTextures()Lnet/minecraft/class_8685;", "method_52814()Lnet/minecraft/class_8685;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getSkinTextures(CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            Object custom = EzzSkinTextureProvider.getCustomSkinTextures(this);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    // 1.16 - 1.20.1 Identifier getSkinTexture() (method_3117)
    @Inject(method = {"getSkinTexture()Lnet/minecraft/class_2960;", "method_3117()Lnet/minecraft/class_2960;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getSkinTexture(CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            Object custom = EzzSkinTextureProvider.getCustomSkinTexture(this);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    // 1.16 - 1.20.1 String getModel() (method_3121)
    @Inject(method = {"getModel()Ljava/lang/String;", "method_3121()Ljava/lang/String;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getModel(CallbackInfoReturnable<String> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            String model = EzzSkinTextureProvider.getCustomModel(this);
            if (model != null) {
                cir.setReturnValue(model);
            }
        }
    }
}
