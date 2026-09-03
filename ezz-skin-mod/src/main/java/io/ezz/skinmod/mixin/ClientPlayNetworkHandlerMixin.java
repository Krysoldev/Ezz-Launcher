package io.ezz.skinmod.mixin;

import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_634", remap = false)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = {"sendChatCommand", "method_45729"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_onSendChatCommand(String command, CallbackInfo ci) {
        if (command != null && (command.equalsIgnoreCase("ezzskin debug") || command.equalsIgnoreCase("ezzskin"))) {
            EzzSkinTextureProvider.printDiagnosticReportToChat();
            ci.cancel();
        }
    }

    @Inject(method = {"sendChatMessage", "method_45730", "method_44099"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void ezz_onSendChatMessage(String message, CallbackInfo ci) {
        if (message != null && (message.equalsIgnoreCase("/ezzskin debug") || message.equalsIgnoreCase("/ezzskin"))) {
            EzzSkinTextureProvider.printDiagnosticReportToChat();
            ci.cancel();
        }
    }
}
