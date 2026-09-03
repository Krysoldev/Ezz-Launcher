package io.ezz.skinmod.mixin;

import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_640", remap = false)
public class PlayerListEntryMixin {

    // 1.20.2+ / 1.21+ SkinTextures getSkinTextures() (method_52810)
    @Inject(method = {"getSkinTextures()Lnet/minecraft/class_8685;", "method_52810()Lnet/minecraft/class_8685;", "method_52810"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getSkinTextures(CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            Object custom = EzzSkinTextureProvider.getCustomSkinTextures(this);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    // 1.16 - 1.20.1 Identifier getSkinTexture() (method_2968)
    @Inject(method = {"getSkinTexture()Lnet/minecraft/class_2960;", "method_2968()Lnet/minecraft/class_2960;", "method_2968"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getSkinTexture(CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            Object custom = EzzSkinTextureProvider.getCustomSkinTexture(this);
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }

    // 1.16 - 1.20.1 String getModel() (method_2977)
    @Inject(method = {"getModel()Ljava/lang/String;", "method_2977()Ljava/lang/String;", "method_2977"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_getModel(CallbackInfoReturnable<String> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(this)) {
            String model = EzzSkinTextureProvider.getCustomModel(this);
            if (model != null) {
                cir.setReturnValue(model);
            }
        }
    }
}
