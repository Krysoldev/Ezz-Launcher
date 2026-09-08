package io.ezz.skinmod.mixin;

import io.ezz.skinmod.common.EzzSkinTextureProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_634", remap = false)
public class ClientPlayNetworkHandlerMixin {

    // 1.19+ sendChatCommand(String) (method_45730)
    @Inject(method = {"sendChatCommand(Ljava/lang/String;)V", "method_45730(Ljava/lang/String;)V"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ezz_onSendChatCommand(String command, CallbackInfo ci) {
        if (command != null && (command.equalsIgnoreCase("ezzskin debug") || command.equalsIgnoreCase("ezzskin"))) {
            EzzSkinTextureProvider.printDiagnosticReportToChat();
            ci.cancel();
        }
    }

    // 1.16 - 1.18 sendChatMessage(String) (method_3142) & 1.19+ sendChatMessage(String) (method_45729)
    @Inject(method = {"sendChatMessage(Ljava/lang/String;)V", "method_45729(Ljava/lang/String;)V", "method_3142(Ljava/lang/String;)V"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ezz_onSendChatMessage(String message, CallbackInfo ci) {
        if (message != null && (message.equalsIgnoreCase("/ezzskin debug") || message.equalsIgnoreCase("/ezzskin"))) {
            EzzSkinTextureProvider.printDiagnosticReportToChat();
            ci.cancel();
        }
    }

    // clearWorld() across all versions:
    // 1.20.4 - 1.21.4+ is method_54134()V
    // 1.19 - 1.20.1 is method_47658()V
    // 1.16 - 1.18.2 is method_2868()V
    // onDisconnected() is method_55505()V
    @Inject(method = {"clearWorld()V", "method_54134()V", "method_47658()V", "method_2868()V", "onDisconnected()V", "method_55505()V"}, at = @At("HEAD"), remap = false, require = 0)
    private void ezz_onClearWorld(CallbackInfo ci) {
        EzzSkinTextureProvider.resetServerOverride();
    }
}
