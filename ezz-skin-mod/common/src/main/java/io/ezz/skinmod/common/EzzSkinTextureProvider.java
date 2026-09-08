package io.ezz.skinmod.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, zero-render-thread-overhead Skin Provider for Ezz Launcher.
 * Features:
 * - O(1) cached UUID / Username matching for local player identification
 * - Single-pass texture decode, GPU registration, and SkinTextures record instantiation
 * - Zero reflection loops in the per-frame render pipeline
 * - Zero render-thread console I/O blocking
 */
public class EzzSkinTextureProvider {

    private static volatile Object registeredIdentifier = null;
    private static volatile Object cachedNativeImage = null;
    private static volatile Object cachedDynamicTexture = null;
    private static volatile Object registeredSkinTextures = null;
    private static volatile boolean textureLoaded = false;
    private static volatile boolean textureBound = false;
    private static volatile boolean enabled = false;
    private static volatile boolean isAlex = false;

    private static volatile UUID cachedLocalUuid = null;
    private static volatile String cachedLocalUsername = null;
    private static volatile String cachedAccountId = null;

    private static int imageWidth = 64;
    private static int imageHeight = 64;
    private static long imageBytesLength = 0;
    private static String computedSha256 = "";
    private static volatile String lastAppliedSource = "DEFAULT";

    // Server-provided skin override state (SkinsRestorer / server skin updates)
    private static volatile boolean serverOverrideActive = false;
    private static volatile String currentServerTextureHash = null;
    private static volatile String baselineClientTextureHash = null;
    private static volatile boolean baselineChecked = false;
    private static volatile long lastSkinFileModified = 0L;

    // Deduplicated diagnostics logging
    private static volatile String lastLoggedSource = null;
    private static volatile String lastLoggedTexture = null;
    private static volatile String lastLoggedReason = null;
    private static volatile String lastLoggedOverrideState = null;

    // Cached reflection handles
    private static final ConcurrentHashMap<Class<?>, Method> UUID_GETTER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Method> NAME_GETTER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Method> PROFILE_GETTER_CACHE = new ConcurrentHashMap<>();

    static {
        initLocalPlayerIdentity();
    }

    public static synchronized void initLocalPlayerIdentity() {
        try {
            EzzSkinConfig config = EzzSkinModCommon.getConfig();
            enabled = config != null && config.enabled;
            isAlex = config != null && ("ALEX".equalsIgnoreCase(config.model) || "SLIM".equalsIgnoreCase(config.model));

            if (config != null) {
                cachedLocalUsername = (config.username != null && !config.username.trim().isEmpty()) 
                    ? config.username.trim() : null;
                cachedAccountId = (config.accountId != null && !config.accountId.trim().isEmpty()) 
                    ? config.accountId.trim() : null;

                if (config.uuid != null && !config.uuid.trim().isEmpty()) {
                    try {
                        cachedLocalUuid = UUID.fromString(config.uuid.trim());
                    } catch (Throwable ignored) {
                        cachedLocalUuid = null;
                    }
                }
            }

            checkBaselineTexture();
        } catch (Throwable ignored) {}
    }

    public static void checkBaselineTexture() {
        if (baselineChecked) return;
        try {
            Object client = getMinecraftClient();
            if (client == null) return;
            Method getSession = getMethodOrNull(client.getClass(), "getSession", "method_1548");
            if (getSession == null) return;
            Object session = getSession.invoke(client);
            if (session == null) return;
            Object sessionProfile = extractGameProfile(session);
            if (sessionProfile != null) {
                String baseVal = extractSkinTextureValue(sessionProfile);
                if (baseVal != null && !baseVal.trim().isEmpty()) {
                    baselineClientTextureHash = calculateSha256(baseVal.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    baselineClientTextureHash = "";
                }
            } else {
                baselineClientTextureHash = "";
            }
            baselineChecked = true;
        } catch (Throwable ignored) {}
    }

    public static void logServerOverride(String state, String reason) {
        String logKey = state + ":" + reason;
        if (logKey.equals(lastLoggedOverrideState)) return;
        lastLoggedOverrideState = logKey;
        System.out.println("[SERVER_SKIN_OVERRIDE] " + state + " - " + reason);
    }

    public static void logSkinSource(Object target, String source, String reason) {
        String uuidStr = "unknown";
        UUID u = extractUuidFast(target);
        if (u != null) {
            uuidStr = u.toString();
        } else if (cachedLocalUuid != null) {
            uuidStr = cachedLocalUuid.toString();
        }
        String texture = "VAULT".equals(source) ? "ezzskin:textures/skin/custom_skin" : "minecraft:server_skin";
        String logKey = uuidStr + ":" + source + ":" + texture + ":" + reason;
        if (logKey.equals(lastLoggedReason)) return;
        lastLoggedReason = logKey;
        lastLoggedSource = source;
        lastLoggedTexture = texture;
        System.out.println("[SKIN_SOURCE] player=" + uuidStr + " source=" + source + " texture=" + texture + " reason=" + reason);
    }

    /**
     * Fast O(1) check to determine if an entity, profile, or UUID is the local player.
     * Zero allocations, zero loops, zero console printing.
     */
    public static boolean isLocalPlayer(Object target) {
        if (!enabled || target == null) return false;

        // 1. Direct UUID match
        if (target instanceof UUID) {
            return cachedLocalUuid != null && cachedLocalUuid.equals(target);
        }

        // 2. Direct String / Username match
        if (target instanceof String) {
            String str = (String) target;
            if (cachedLocalUsername != null && cachedLocalUsername.equalsIgnoreCase(str)) return true;
            if (cachedAccountId != null && cachedAccountId.equalsIgnoreCase(str)) return true;
            return false;
        }

        // 3. Fast UUID / Profile extraction from GameProfile, PlayerListEntry, or AbstractClientPlayerEntity
        try {
            UUID targetUuid = extractUuidFast(target);
            if (targetUuid != null && cachedLocalUuid != null && cachedLocalUuid.equals(targetUuid)) {
                return true;
            }

            String targetName = extractUsernameFast(target);
            if (targetName != null) {
                if (cachedLocalUsername != null && cachedLocalUsername.equalsIgnoreCase(targetName)) return true;
                if (cachedAccountId != null && cachedAccountId.equalsIgnoreCase(targetName)) return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public static boolean hasServerSkinOverride() {
        if (!enabled) return false;
        if (isSingleplayer()) return false;
        return serverOverrideActive;
    }

    public static boolean shouldApplyVaultSkin(Object target) {
        if (!enabled) return false;
        if (target != null && !isLocalPlayer(target)) return false;
        if (isSingleplayer()) {
            logSkinSource(target, "VAULT", "Singleplayer integrated server");
            return true;
        }
        if (hasServerSkinOverride()) {
            logSkinSource(target, "SERVER", "Server skin override active");
            return false;
        }
        logSkinSource(target, "VAULT", "Multiplayer without server override");
        return true;
    }

    public static synchronized void resetServerOverride() {
        boolean wasActive = serverOverrideActive;
        serverOverrideActive = false;
        currentServerTextureHash = null;
        lastAppliedSource = "EZZ_VAULT";
        logServerOverride("CLEARED", "Disconnect or world reset");
        if (wasActive) {
            System.out.println("[EZZ-SKIN] DISCONNECT_RESET: Cleared server skin override, Vault fallback restored.");
        }
    }

    public static synchronized void onGameJoin() {
        serverOverrideActive = false;
        currentServerTextureHash = null;
        lastAppliedSource = "EZZ_VAULT";
        baselineChecked = false;
        checkBaselineTexture();
        logServerOverride("CLEARED", "Game join initialized");
        System.out.println("[EZZ-SKIN] GAME_JOIN: Initialized session state, Vault fallback active.");
    }

    public static void updateServerSkinState(Object target) {
        if (!enabled || target == null || isSingleplayer()) {
            if (serverOverrideActive) {
                serverOverrideActive = false;
                logServerOverride("CLEARED", "Singleplayer or mod disabled");
            }
            return;
        }

        try {
            checkBaselineTexture();

            String rawTextureVal = extractSkinTextureValue(target);
            if (rawTextureVal == null || rawTextureVal.trim().isEmpty()) {
                if (serverOverrideActive) {
                    serverOverrideActive = false;
                    currentServerTextureHash = null;
                    lastAppliedSource = "EZZ_VAULT";
                    logServerOverride("CLEARED", "No server texture present in profile");
                    logSkinSource(target, "VAULT", "No server texture in profile");
                }
                return;
            }

            String currentHash = calculateSha256(rawTextureVal.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            boolean isOverride = false;
            String overrideReason = "";

            if (baselineClientTextureHash == null || baselineClientTextureHash.isEmpty()) {
                // Offline launcher account has no Mojang session skin.
                // Any texture present on the server was authoritatively provided by the server (e.g. SkinsRestorer)
                isOverride = true;
                overrideReason = "SERVER_SUPPLIED_TEXTURE_OFFLINE_PLAYER";
            } else if (!currentHash.equals(baselineClientTextureHash)) {
                // Online account whose server texture differs from its initial Mojang baseline:
                // Server plugin (SkinsRestorer /skin) has overridden the skin
                isOverride = true;
                overrideReason = "SERVER_TEXTURE_DIFFERS_FROM_MOJANG_BASELINE";
            }

            if (isOverride) {
                if (!serverOverrideActive || !currentHash.equals(currentServerTextureHash)) {
                    boolean isUpdate = serverOverrideActive;
                    serverOverrideActive = true;
                    currentServerTextureHash = currentHash;
                    lastAppliedSource = "SERVER_OVERRIDE";
                    String shortH = currentHash.length() > 12 ? currentHash.substring(0, 12) : currentHash;
                    logServerOverride(isUpdate ? "UPDATED" : "ACTIVE", overrideReason + " (hash: " + shortH + ")");
                    logSkinSource(target, "SERVER", overrideReason);
                }
            } else {
                if (serverOverrideActive) {
                    serverOverrideActive = false;
                    currentServerTextureHash = null;
                    lastAppliedSource = "EZZ_VAULT";
                    logServerOverride("CLEARED", "Server texture matches Mojang baseline");
                    logSkinSource(target, "VAULT", "Server texture matches Mojang baseline");
                }
            }
        } catch (Throwable ignored) {}
    }

    public static Object extractGameProfile(Object target) {
        if (target == null) return null;
        if (target.getClass().getName().endsWith("GameProfile")) return target;

        Class<?> clazz = target.getClass();
        Method profMethod = PROFILE_GETTER_CACHE.get(clazz);
        if (profMethod == null) {
            profMethod = getMethodOrNull(clazz,
                "getProfile", "method_2966", "comp_868", "profile",
                "getGameProfile", "method_7334", "method_53462",
                "getPlayerListEntry", "method_3123"
            );
            if (profMethod != null) {
                PROFILE_GETTER_CACHE.put(clazz, profMethod);
            }
        }

        if (profMethod != null) {
            try {
                Object res = profMethod.invoke(target);
                if (res != null) {
                    if (res.getClass().getName().endsWith("GameProfile")) {
                        return res;
                    }
                    return extractGameProfile(res);
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    public static String extractSkinTextureValue(Object target) {
        if (target == null) return null;
        try {
            Object profile = extractGameProfile(target);
            if (profile == null) profile = target;

            Method getPropertiesMethod = getMethodOrNull(profile.getClass(), "getProperties");
            if (getPropertiesMethod != null) {
                Object propsObj = getPropertiesMethod.invoke(profile);
                if (propsObj != null) {
                    Method getMethod = getMethodOrNull(propsObj.getClass(), "get");
                    if (getMethod != null) {
                        Object col = getMethod.invoke(propsObj, "textures");
                        if (col instanceof java.util.Collection) {
                            for (Object p : (java.util.Collection<?>) col) {
                                Method getName = getMethodOrNull(p.getClass(), "name", "getName");
                                Object nameObj = getName != null ? getName.invoke(p) : null;
                                if ("textures".equals(nameObj)) {
                                    Method getVal = getMethodOrNull(p.getClass(), "value", "getValue");
                                    if (getVal != null) {
                                        Object v = getVal.invoke(p);
                                        if (v instanceof String && !((String) v).isEmpty()) {
                                            return (String) v;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object getCustomSkinTexture() {
        return getCustomSkinTexture(null);
    }

    public static Object getCustomSkinTexture(Object entity) {
        if (!enabled) return null;
        if (hasServerSkinOverride()) return null;
        if (entity != null && !isLocalPlayer(entity)) return null;

        ensureTextureLoaded();
        bindTextureIfReady();

        return registeredIdentifier;
    }

    public static Object getCustomSkinTextures() {
        return getCustomSkinTextures(null);
    }

    public static Object getCustomSkinTextures(Object entity) {
        if (!enabled) return null;
        if (hasServerSkinOverride()) return null;
        if (entity != null && !isLocalPlayer(entity)) return null;

        ensureTextureLoaded();
        bindTextureIfReady();

        if (registeredSkinTextures != null) {
            return registeredSkinTextures;
        }

        if (registeredIdentifier != null) {
            registeredSkinTextures = createSkinTexturesRecord(registeredIdentifier, isAlex);
            return registeredSkinTextures;
        }

        return null;
    }

    public static String getCustomModel(Object entity) {
        if (!enabled) return null;
        if (hasServerSkinOverride()) return null;
        if (entity != null && !isLocalPlayer(entity)) return null;
        return isAlex ? "slim" : "default";
    }

    public static synchronized void ensureTextureLoaded() {
        initLocalPlayerIdentity();
        EzzSkinConfig config = EzzSkinModCommon.getConfig();
        if (config == null || !config.enabled) return;

        try {
            File skinFile = new File(config.skinFile);
            if (!skinFile.isAbsolute()) {
                File gameDir = getGameDirectory();
                skinFile = new File(gameDir, config.skinFile);
            }

            if (!skinFile.exists()) {
                System.err.println("[EZZ-SKIN] Skin file not found at " + skinFile.getAbsolutePath());
                return;
            }

            long modified = skinFile.lastModified();
            if (textureLoaded && modified == lastSkinFileModified) {
                return;
            }

            textureLoaded = true;
            lastSkinFileModified = modified;
            textureBound = false;
            registeredSkinTextures = null;

            imageBytesLength = skinFile.length();
            byte[] fileBytes = readFileBytes(skinFile);
            computedSha256 = calculateSha256(fileBytes);

            String hash = config.skinHash;
            String safeHash = (hash != null && !hash.isEmpty()) ? hash : "custom";
            registeredIdentifier = createIdentifier("ezzskin", "textures/skin/" + safeHash);

            cachedNativeImage = readNativeImage(skinFile);
            if (cachedNativeImage != null) {
                imageWidth = getNativeImageWidth(cachedNativeImage);
                imageHeight = getNativeImageHeight(cachedNativeImage);
            }

            if (registeredIdentifier != null) {
                registeredSkinTextures = createSkinTexturesRecord(registeredIdentifier, isAlex);
                lastAppliedSource = "EZZ_VAULT";
            }
        } catch (Throwable t) {
            System.err.println("[EZZ-SKIN] Error loading skin texture: " + t.getMessage());
        }
    }

    public static synchronized void bindTextureIfReady() {
        if (textureBound) return;
        if (registeredIdentifier == null || cachedNativeImage == null) return;

        try {
            Object client = getMinecraftClient();
            if (client == null) return;
            Method getTextureManager = getMethodOrNull(client.getClass(), "getTextureManager", "method_1531");
            if (getTextureManager == null) return;
            Object manager = getTextureManager.invoke(client);
            if (manager == null) return;

            if (cachedDynamicTexture == null) {
                cachedDynamicTexture = createNativeImageBackedTexture(cachedNativeImage);
            }

            if (cachedDynamicTexture == null) return;

            Method register = getMethodOrNull(manager.getClass(), "registerTexture", "method_4616");
            if (register != null) {
                register.invoke(manager, registeredIdentifier, cachedDynamicTexture);
                textureBound = true;
            }
        } catch (Throwable ignored) {}
    }

    private static UUID extractUuidFast(Object target) {
        if (target == null) return null;
        if (target instanceof UUID) return (UUID) target;

        Class<?> clazz = target.getClass();
        Method m = UUID_GETTER_CACHE.get(clazz);
        if (m == null) {
            m = getMethodOrNull(clazz, "getUuid", "method_5667", "getId", "method_2966", "id");
            if (m != null) {
                UUID_GETTER_CACHE.put(clazz, m);
            }
        }

        if (m != null) {
            try {
                Object res = m.invoke(target);
                if (res instanceof UUID) return (UUID) res;
                if (res != null) return extractUuidFast(res);
            } catch (Throwable ignored) {}
        }

        // Profile fallback
        Object prof = extractGameProfile(target);
        if (prof != null && prof != target) {
            return extractUuidFast(prof);
        }

        return null;
    }

    private static String extractUsernameFast(Object target) {
        if (target == null) return null;
        if (target instanceof String) return (String) target;

        Class<?> clazz = target.getClass();
        Method m = NAME_GETTER_CACHE.get(clazz);
        if (m == null) {
            m = getMethodOrNull(clazz, "getName", "method_5477", "getString", "name");
            if (m != null) {
                NAME_GETTER_CACHE.put(clazz, m);
            }
        }

        if (m != null) {
            try {
                Object res = m.invoke(target);
                if (res instanceof String) return (String) res;
                if (res != null) return res.toString();
            } catch (Throwable ignored) {}
        }

        return null;
    }

    public static String[] getDiagnosticReportLines() {
        List<String> list = new ArrayList<>();
        list.add("§6=== §eEzz Skin Mod Diagnostic §6===");
        list.add("§aStatus: §f" + (enabled ? "ACTIVE (Context-Aware Priority)" : "INACTIVE"));
        list.add("§aUsername: §f" + (cachedLocalUsername != null ? cachedLocalUsername : "N/A"));
        list.add("§aUUID: §f" + (cachedLocalUuid != null ? cachedLocalUuid.toString() : "N/A"));
        list.add("§aModel: §f" + (isAlex ? "SLIM (Alex)" : "WIDE (Steve)"));
        list.add("§aEnvironment: §f" + (isSingleplayer() ? "SINGLEPLAYER" : "MULTIPLAYER"));
        list.add("§aServer Skin Override: §f" + (hasServerSkinOverride() ? "ACTIVE (SkinRestorer / Server Skin)" : "NONE (Vault Fallback)"));
        list.add("§aSkin Source: §f" + getLastAppliedSource());
        list.add("§aVault Texture ID: §f" + (registeredIdentifier != null ? registeredIdentifier.toString() : "NOT_LOADED"));
        list.add("§aVault Texture Bound: §f" + (textureBound ? "YES" : "NO"));
        if (currentServerTextureHash != null && !currentServerTextureHash.isEmpty()) {
            list.add("§aServer Texture Hash: §f" + (currentServerTextureHash.length() > 16 ? currentServerTextureHash.substring(0, 16) + "..." : currentServerTextureHash));
        }
        list.add("§aVault Dimensions: §f" + imageWidth + "x" + imageHeight);
        if (imageBytesLength > 0) {
            list.add("§aVault File Size: §f" + imageBytesLength + " bytes");
        }
        if (computedSha256 != null && !computedSha256.isEmpty()) {
            list.add("§aVault SHA-256: §f" + (computedSha256.length() > 16 ? computedSha256.substring(0, 16) + "..." : computedSha256));
        }
        list.add("§6=================================");
        return list.toArray(new String[0]);
    }

    public static int getImageWidth() {
        return imageWidth;
    }

    public static int getImageHeight() {
        return imageHeight;
    }

    public static long getImageBytesLength() {
        return imageBytesLength;
    }

    public static String getComputedSha256() {
        return computedSha256;
    }

    public static String getLastAppliedSource() {
        if (!enabled) return "DEFAULT";
        if (hasServerSkinOverride()) return "SERVER_OVERRIDE";
        return lastAppliedSource;
    }

    public static void printDiagnosticReportToChat() {
        try {
            Object client = getMinecraftClient();
            if (client == null) return;
            Field inGameHudField = getFieldOrNull(client, "inGameHud", "field_1705");
            if (inGameHudField == null) return;
            Object inGameHud = inGameHudField.get(client);
            if (inGameHud == null) return;

            Method getChatHudMethod = getMethodOrNull(inGameHud.getClass(), "getChatHud", "method_1743");
            if (getChatHudMethod == null) return;
            Object chatHud = getChatHudMethod.invoke(inGameHud);
            if (chatHud == null) return;

            Method addMessageMethod = getMethodOrNull(chatHud.getClass(), "addMessage", "method_1812");
            if (addMessageMethod == null) return;

            Class<?> textClass = findClass("net.minecraft.text.Text", "net.minecraft.class_2561");
            Method literalMethod = getMethodOrNull(textClass, "literal", "method_43470", "of");

            String[] lines = getDiagnosticReportLines();
            for (String line : lines) {
                Object textObj = (literalMethod != null) ? literalMethod.invoke(null, line) : line;
                addMessageMethod.invoke(chatHud, textObj);
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isSingleplayer() {
        try {
            Object client = getMinecraftClient();
            if (client != null) {
                Method isIntegrated = getMethodOrNull(client.getClass(), "isIntegratedServerRunning", "method_1542", "isInSingleplayer");
                if (isIntegrated != null) {
                    Object res = isIntegrated.invoke(client);
                    if (res instanceof Boolean) return (Boolean) res;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Object getMinecraftClient() {
        try {
            Class<?> clientClass = findClass("net.minecraft.client.MinecraftClient", "net.minecraft.class_310");
            if (clientClass == null) return null;
            Method getInstance = getMethodOrNull(clientClass, "getInstance", "method_1551");
            if (getInstance != null) {
                return getInstance.invoke(null);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static File getGameDirectory() {
        try {
            Object client = getMinecraftClient();
            if (client != null) {
                Field runDirField = getFieldOrNull(client, "runDirectory", "field_1697");
                if (runDirField != null) {
                    return (File) runDirField.get(client);
                }
            }
        } catch (Throwable ignored) {}
        return new File(".");
    }

    private static Object createIdentifier(String namespace, String path) {
        try {
            Class<?> idClass = findClass("net.minecraft.util.Identifier", "net.minecraft.class_2960", "net.minecraft.resources.ResourceLocation");
            if (idClass == null) return null;

            for (Method m : idClass.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == idClass) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 2 && pts[0] == String.class && pts[1] == String.class) {
                        try {
                            Object res = m.invoke(null, namespace, path);
                            if (res != null) return res;
                        } catch (Throwable ignored) {}
                    }
                }
            }

            for (Constructor<?> ctor : idClass.getConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length == 2 && pts[0] == String.class && pts[1] == String.class) {
                    return ctor.newInstance(namespace, path);
                }
                if (pts.length == 1 && pts[0] == String.class) {
                    return ctor.newInstance(namespace + ":" + path);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object readNativeImage(File file) {
        try {
            Class<?> imageClass = findClass("net.minecraft.client.texture.NativeImage", "net.minecraft.class_1011", "com.mojang.blaze3d.platform.NativeImage");
            if (imageClass == null) return null;

            byte[] bytes = readFileBytes(file);

            for (Method m : imageClass.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == imageClass) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 1 && pts[0] == byte[].class) {
                        try {
                            Object img = m.invoke(null, (Object) bytes);
                            if (img != null) return img;
                        } catch (Throwable ignored) {}
                    }
                }
            }

            for (Method m : imageClass.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == imageClass) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 1 && pts[0] == InputStream.class) {
                        try (InputStream is = new FileInputStream(file)) {
                            Object img = m.invoke(null, is);
                            if (img != null) return img;
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int getNativeImageWidth(Object nativeImage) {
        try {
            Method m = getMethodOrNull(nativeImage.getClass(), "getWidth", "method_4307");
            if (m != null) return (Integer) m.invoke(nativeImage);
        } catch (Throwable ignored) {}
        return 64;
    }

    private static int getNativeImageHeight(Object nativeImage) {
        try {
            Method m = getMethodOrNull(nativeImage.getClass(), "getHeight", "method_4323");
            if (m != null) return (Integer) m.invoke(nativeImage);
        } catch (Throwable ignored) {}
        return 64;
    }

    private static Object createNativeImageBackedTexture(Object nativeImage) {
        try {
            Class<?> textureClass = findClass("net.minecraft.client.texture.NativeImageBackedTexture", "net.minecraft.class_1043", "net.minecraft.client.renderer.texture.DynamicTexture");
            if (textureClass == null || nativeImage == null) return null;

            for (Constructor<?> ctor : textureClass.getConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length == 2 && pts[0] == java.util.function.Supplier.class && pts[1].isAssignableFrom(nativeImage.getClass())) {
                    java.util.function.Supplier<String> nameSupplier = () -> "ezz_skin";
                    return ctor.newInstance(nameSupplier, nativeImage);
                }
            }

            for (Constructor<?> ctor : textureClass.getConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length == 2 && pts[0] == String.class && pts[1].isAssignableFrom(nativeImage.getClass())) {
                    return ctor.newInstance("ezz_skin", nativeImage);
                }
            }

            for (Constructor<?> ctor : textureClass.getConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length == 1 && pts[0].isAssignableFrom(nativeImage.getClass())) {
                    return ctor.newInstance(nativeImage);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object createSkinAsset(Object identifier) {
        if (identifier == null) return null;
        try {
            Class<?> idClass = findClass("net.minecraft.util.Identifier", "net.minecraft.class_2960");
            Class<?> assetClassA = findClass("net.minecraft.class_12079$class_12080", "net.minecraft.client.util.SkinTextures$Asset");
            Class<?> assetClassB = findClass("net.minecraft.class_12079$class_10726");

            if (assetClassA != null) {
                for (Constructor<?> ctor : assetClassA.getConstructors()) {
                    if (ctor.getParameterCount() == 2 && ctor.getParameterTypes()[0].isAssignableFrom(idClass)) {
                        return ctor.newInstance(identifier, null);
                    }
                    if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isAssignableFrom(idClass)) {
                        return ctor.newInstance(identifier);
                    }
                }
            }

            if (assetClassB != null) {
                for (Constructor<?> ctor : assetClassB.getConstructors()) {
                    if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isAssignableFrom(idClass)) {
                        return ctor.newInstance(identifier);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return identifier;
    }

    private static Object createSkinTexturesRecord(Object identifier, boolean isSlim) {
        if (identifier == null) return null;
        try {
            Class<?> skinTexturesClass = findClass("net.minecraft.client.util.SkinTextures", "net.minecraft.class_8685");
            if (skinTexturesClass == null) return null;

            Class<?> modelEnumClass = findClass("net.minecraft.client.util.SkinTextures$Model", "net.minecraft.class_7920", "net.minecraft.class_8685$class_8686", "ddp");

            Object model = null;
            if (modelEnumClass != null && modelEnumClass.isEnum()) {
                Object[] constants = modelEnumClass.getEnumConstants();
                for (Object c : constants) {
                    if (isSlim && (c.toString().equalsIgnoreCase("SLIM") || c.toString().equalsIgnoreCase("b"))) model = c;
                    if (!isSlim && (c.toString().equalsIgnoreCase("WIDE") || c.toString().equalsIgnoreCase("DEFAULT") || c.toString().equalsIgnoreCase("a"))) model = c;
                }
                if (model == null && constants.length > 0) {
                    model = isSlim && constants.length > 1 ? constants[1] : constants[0];
                }
            }

            Object skinAsset = createSkinAsset(identifier);

            for (Method m : skinTexturesClass.getMethods()) {
                if (m.getReturnType() == skinTexturesClass && m.getParameterCount() == 4) {
                    try {
                        Object res = m.invoke(null, skinAsset, null, null, model);
                        if (res != null) return res;
                    } catch (Throwable ignored) {}
                }
            }

            for (Constructor<?> ctor : skinTexturesClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 5) {
                    try {
                        Object res = ctor.newInstance(skinAsset, null, null, model, true);
                        if (res != null) return res;
                    } catch (Throwable ignored) {}
                }
            }

            for (Constructor<?> ctor : skinTexturesClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 6) {
                    try {
                        Object res = ctor.newInstance(identifier, null, null, null, model, true);
                        if (res != null) return res;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static byte[] readFileBytes(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int r = is.read(bytes, read, bytes.length - read);
                if (r == -1) break;
                read += r;
            }
            return bytes;
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static Class<?> findClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
            } catch (Throwable ignored) {}
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Method getMethodOrNull(Class<?> clazz, String... names) {
        if (clazz == null) return null;
        for (String name : names) {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name)) return m;
            }
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static Field getFieldOrNull(Object target, String... names) {
        if (target == null) return null;
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            for (String name : names) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getName().equals(name)) {
                        f.setAccessible(true);
                        return f;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
