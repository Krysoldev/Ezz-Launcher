package io.ezz.skinmod.common;

/**
 * Common skin renderer interface that isolates Minecraft version-specific
 * texture registration, rendering hooks, and model conversion from shared business logic.
 */
public interface SkinRendererAdapter {

    /**
     * Initializes the client renderer hook.
     */
    void initialize();

    /**
     * Retrieves the custom Identifier texture for legacy Minecraft versions (1.16 - 1.20.1).
     */
    Object getLocalPlayerSkin(Object target);

    /**
     * Retrieves the custom SkinTextures record for modern Minecraft versions (1.20.2+ / 1.21+ / 1.26+).
     */
    Object getLocalPlayerSkinTextures(Object target);

    /**
     * Retrieves the model string ("default" or "slim").
     */
    String getLocalPlayerModel(Object target);

    /**
     * Checks if the target player has a server-provided skin (e.g. SkinRestorer / Mojang).
     */
    boolean hasServerSkin(Object target);

    /**
     * Checks if the target entity / profile belongs to the local player.
     */
    boolean isLocalPlayer(Object target);

    /**
     * Prints the diagnostic report to in-game chat or system log.
     */
    void printDiagnostics();
}
