package io.ezz.skinmod.mixin;

import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

/**
 * Legacy Mixin for DefaultSkinHelper (net.minecraft.class_1068) in Minecraft 1.16.x through 1.20.1.
 */
@Mixin(targets = "net.minecraft.class_1068", remap = false)
public class DefaultSkinHelperLegacyMixin {

    @Inject(method = {"getTexture(Ljava/util/UUID;)Lnet/minecraft/class_2960;", "method_4648(Ljava/util/UUID;)Lnet/minecraft/class_2960;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezz_getTextureFromUuid(UUID uuid, CallbackInfoReturnable<Object> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(uuid)) {
            if (!EzzSkinTextureProvider.hasServerSkinOverride()) {
                Object custom = EzzSkinTextureProvider.getCustomSkinTexture(uuid);
                if (custom != null) {
                    cir.setReturnValue(custom);
                }
            }
        }
    }

    @Inject(method = {"getModel(Ljava/util/UUID;)Ljava/lang/String;", "method_4649(Ljava/util/UUID;)Ljava/lang/String;"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void ezz_getModelFromUuid(UUID uuid, CallbackInfoReturnable<String> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(uuid)) {
            if (!EzzSkinTextureProvider.hasServerSkinOverride()) {
                String model = EzzSkinTextureProvider.getCustomModel(uuid);
                if (model != null) {
                    cir.setReturnValue(model);
                }
            }
        }
    }
}
