package io.ezz.skinmod.mixin;

import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Legacy Mixin for PlayerListEntry (net.minecraft.class_640) in Minecraft 1.16.x through 1.20.1.
 */
@Mixin(targets = "net.minecraft.class_640", remap = false)
public class PlayerListEntryLegacyMixin {

    @Inject(method = {"getSkinTexture()Lnet/minecraft/class_2960;", "method_2968()Lnet/minecraft/class_2960;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getSkinTexture(CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            Object custom = EzzSkinTextureProvider.getCustomSkinTexture(this);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    @Inject(method = {"getModel()Ljava/lang/String;", "method_2977()Ljava/lang/String;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getModel(CallbackInfoReturnable<String> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            String model = EzzSkinTextureProvider.getCustomModel(this);
            if (model != null) {
                cir.setReturnValue(model);
            }
        }
    }

    @Inject(method = {"hasSkinTexture()Z", "method_2979()Z"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_hasSkinTexture(CallbackInfoReturnable<Boolean> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            cir.setReturnValue(true);
        }
    }
}
