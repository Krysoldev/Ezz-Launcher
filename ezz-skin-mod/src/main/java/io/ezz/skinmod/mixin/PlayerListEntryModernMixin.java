package io.ezz.skinmod.mixin;

import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modern Mixin for PlayerListEntry (net.minecraft.class_640) in Minecraft 1.20.2+, 1.21.x, and 1.26.x.
 * Targets exclusively getSkinTextures()Lnet/minecraft/class_8685; (method_52810).
 */
@Mixin(targets = "net.minecraft.class_640", remap = false)
public class PlayerListEntryModernMixin {

    @Inject(method = {"getSkinTextures()Lnet/minecraft/class_8685;", "method_52810()Lnet/minecraft/class_8685;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getSkinTextures(CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            EzzSkinTextureProvider.updateServerSkinState(this);
            if (EzzSkinTextureProvider.shouldApplyVaultSkin(this)) {
                Object custom = EzzSkinTextureProvider.getCustomSkinTextures(this);
                if (custom != null) {
                    cir.setReturnValue(custom);
                }
            }
        }
    }
}
