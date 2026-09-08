package io.ezz.skinmod.mixin;

import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modern Mixin for AbstractClientPlayerEntity (net.minecraft.class_742) in Minecraft 1.20.2+, 1.21.x, and 1.26.x.
 * Targets exclusively getSkinTextures()Lnet/minecraft/class_8685; (method_52814).
 * Absolutely no references to legacy getSkinTexture() or method_3118 (which is getFovMultiplier() returning float).
 */
@Mixin(targets = "net.minecraft.class_742", remap = false)
public class AbstractClientPlayerEntityModernMixin {

    @Inject(method = {"getSkinTextures()Lnet/minecraft/class_8685;", "method_52814()Lnet/minecraft/class_8685;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getSkinTextures(CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            Object custom = EzzSkinTextureProvider.getCustomSkinTextures(this);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }
}
