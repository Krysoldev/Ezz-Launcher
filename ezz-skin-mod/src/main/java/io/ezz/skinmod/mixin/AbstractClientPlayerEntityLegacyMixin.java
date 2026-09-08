package io.ezz.skinmod.mixin;

import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Legacy Mixin for AbstractClientPlayerEntity (net.minecraft.class_742) in Minecraft 1.16.x through 1.20.1.
 * Targets getSkinTexture()Lnet/minecraft/class_2960; (method_3117) and getModel()Ljava/lang/String; (method_3121).
 * Never references method_3118 (which is getSpeed/getFovMultiplier returning float).
 */
@Mixin(targets = "net.minecraft.class_742", remap = false)
public class AbstractClientPlayerEntityLegacyMixin {

    @Inject(method = {"getSkinTexture()Lnet/minecraft/class_2960;", "method_3117()Lnet/minecraft/class_2960;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getSkinTexture(CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            EzzSkinTextureProvider.updateServerSkinState(this);
            if (!EzzSkinTextureProvider.hasServerSkinOverride()) {
                Object custom = EzzSkinTextureProvider.getCustomSkinTexture(this);
                if (custom != null) {
                    cir.setReturnValue(custom);
                }
            }
        }
    }

    @Inject(method = {"getModel()Ljava/lang/String;", "method_3121()Ljava/lang/String;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getModel(CallbackInfoReturnable<String> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            if (!EzzSkinTextureProvider.hasServerSkinOverride()) {
                String model = EzzSkinTextureProvider.getCustomModel(this);
                if (model != null) {
                    cir.setReturnValue(model);
                }
            }
        }
    }
}
