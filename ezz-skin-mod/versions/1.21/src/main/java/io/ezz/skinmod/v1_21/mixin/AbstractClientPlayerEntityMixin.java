package io.ezz.skinmod.v1_21.mixin;

import com.mojang.authlib.GameProfile;
import io.ezz.skinmod.v1_21.EzzSkinMod121;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
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
     * Injects into getSkinTextures to supply the local Ezz Vault skin ONLY for the local client player.
     * Respects server-provided skins (e.g. SkinsRestorer / Mojang auth).
     */
    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void ezz_getSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // 1. Check if this entity is the local client player
        if (!this.getUuid().equals(client.player.getUuid())) {
            return; // Remote player in multiplayer: DO NOT MODIFY!
        }

        // 2. Check if a server skin plugin (e.g. SkinsRestorer) has provided a custom skin
        PlayerListEntry entry = this.getPlayerListEntry();
        if (entry != null && entry.getSkinTextures() != null) {
            SkinTextures serverSkin = entry.getSkinTextures();
            String path = serverSkin.texture().getPath();
            if (!path.contains("entity/player/wide") && !path.contains("entity/player/slim") && !path.contains("steve") && !path.contains("alex")) {
                return;
            }
        }

        // 3. Fallback to local Ezz Vault skin for local player
        SkinTextures custom = EzzSkinMod121.getCustomSkinTextures();
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}
