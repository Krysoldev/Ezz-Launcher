package io.ezz.skinmod.v1_16.mixin;

import com.mojang.authlib.GameProfile;
import io.ezz.skinmod.common.EzzSkinModCommon;
import io.ezz.skinmod.v1_16.EzzSkinMod116;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin extends PlayerEntity {

    public AbstractClientPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Shadow
    @Nullable
    protected abstract PlayerListEntry getPlayerListEntry();

    /**
     * Injects into getSkinTexture to supply the local Ezz Vault skin ONLY for the local client player.
     * Respects server-provided skins (e.g. SkinsRestorer / Mojang auth).
     */
    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void ezz_getSkinTexture(CallbackInfoReturnable<Identifier> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // 1. Check if this entity is the local client player
        if (!this.getUuid().equals(client.player.getUuid())) {
            return; // Remote player in multiplayer: DO NOT MODIFY!
        }

        // 2. Check if a server skin plugin (e.g. SkinsRestorer) has provided a custom skin texture
        PlayerListEntry entry = this.getPlayerListEntry();
        if (entry != null && entry.hasSkinTexture()) {
            return; // Server-provided skin takes priority!
        }

        // 3. Fallback to local Ezz Vault skin for local player
        Identifier custom = EzzSkinMod116.getCustomSkinIdentifier();
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }

    /**
     * Injects into getModel to supply "slim" (Alex) or "default" (Steve) arm geometry.
     */
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void ezz_getModel(CallbackInfoReturnable<String> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (!this.getUuid().equals(client.player.getUuid())) {
            return; // Remote player: leave untouched
        }

        PlayerListEntry entry = this.getPlayerListEntry();
        if (entry != null && entry.hasSkinTexture()) {
            return; // Server model takes priority
        }

        if (EzzSkinMod116.getCustomSkinIdentifier() != null) {
            cir.setReturnValue(EzzSkinModCommon.isAlexModel() ? "slim" : "default");
        }
    }
}
