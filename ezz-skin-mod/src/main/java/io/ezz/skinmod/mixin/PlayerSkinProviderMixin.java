package io.ezz.skinmod.mixin;

import com.mojang.authlib.GameProfile;
import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Mixin(targets = "net.minecraft.class_1071", remap = false)
public class PlayerSkinProviderMixin {

    // 1.20.2+ / 1.21+ CompletableFuture<Optional<SkinTextures>> fetchSkinTextures(GameProfile profile)
    @Inject(method = {"fetchSkinTextures(Lcom/mojang/authlib/GameProfile;)Ljava/util/concurrent/CompletableFuture;", "method_52863(Lcom/mojang/authlib/GameProfile;)Ljava/util/concurrent/CompletableFuture;"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ezz_fetchSkinTextures(GameProfile profile, CallbackInfoReturnable<CompletableFuture<Optional<Object>>> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(profile)) {
            EzzSkinTextureProvider.updateServerSkinState(profile);
            if (!EzzSkinTextureProvider.hasServerSkinOverride()) {
                String val = EzzSkinTextureProvider.extractSkinTextureValue(profile);
                if (val == null || val.trim().isEmpty()) {
                    Object custom = EzzSkinTextureProvider.getCustomSkinTextures(profile);
                    if (custom != null) {
                        cir.setReturnValue(CompletableFuture.completedFuture(Optional.of(custom)));
                    }
                }
            }
        }
    }

    // 1.20.2+ / 1.21+ Supplier<SkinTextures> getSkinTexturesSupplier(GameProfile profile, boolean requireSecure)
    @Inject(method = {"getSkinTexturesSupplier(Lcom/mojang/authlib/GameProfile;Z)Ljava/util/function/Supplier;", "method_73544(Lcom/mojang/authlib/GameProfile;Z)Ljava/util/function/Supplier;"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ezz_getSkinTexturesSupplier(GameProfile profile, boolean requireSecure, CallbackInfoReturnable<Supplier<Object>> cir) {
        if (EzzSkinTextureProvider.isLocalPlayer(profile)) {
            EzzSkinTextureProvider.updateServerSkinState(profile);
            if (!EzzSkinTextureProvider.hasServerSkinOverride()) {
                String val = EzzSkinTextureProvider.extractSkinTextureValue(profile);
                if (val == null || val.trim().isEmpty()) {
                    Object custom = EzzSkinTextureProvider.getCustomSkinTextures(profile);
                    if (custom != null) {
                        cir.setReturnValue(() -> custom);
                    }
                }
            }
        }
    }
}
