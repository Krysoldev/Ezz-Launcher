package io.ezz.skinmod.v1_19.mixin;

import com.mojang.authlib.GameProfile;
import io.ezz.skinmod.common.EzzSkinModCommon;
import io.ezz.skinmod.v1_19.EzzSkinMod119;
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

    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void ezz_getSkinTexture(CallbackInfoReturnable<Identifier> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (!this.getUuid().equals(client.player.getUuid())) {
            return;
        }

        PlayerListEntry entry = this.getPlayerListEntry();
        if (entry != null && entry.hasSkinTexture()) {
            return;
        }

        Identifier custom = EzzSkinMod119.getCustomSkinIdentifier();
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void ezz_getModel(CallbackInfoReturnable<String> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (!this.getUuid().equals(client.player.getUuid())) {
            return;
        }

        PlayerListEntry entry = this.getPlayerListEntry();
        if (entry != null && entry.hasSkinTexture()) {
            return;
        }

        if (EzzSkinMod119.getCustomSkinIdentifier() != null) {
            cir.setReturnValue(EzzSkinModCommon.isAlexModel() ? "slim" : "default");
        }
    }
}
