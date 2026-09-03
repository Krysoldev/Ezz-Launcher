package io.ezz.skinmod.common;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

public class EzzSkinConfig {
    public boolean enabled = false;
    public String username = "";
    public String uuid = "";
    public String accountId = "";
    public String skinId = "";
    public String skinHash = "";
    public String model = "STEVE"; // STEVE (classic) or ALEX (slim)
    public String skinFile = "config/ezz-skin/skin.png";

    public static EzzSkinConfig load(File configFile) {
        EzzSkinConfig config = new EzzSkinConfig();
        if (configFile == null || !configFile.exists()) {
            return config;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String content = sb.toString();

            config.enabled = parseBooleanField(content, "enabled", false);
            config.username = parseStringField(content, "username", "");
            config.uuid = parseStringField(content, "uuid", "");
            config.accountId = parseStringField(content, "accountId", "");
            config.skinId = parseStringField(content, "skinId", "");
            config.skinHash = parseStringField(content, "skinHash", "");
            config.model = parseStringField(content, "model", "STEVE");
            config.skinFile = parseStringField(content, "skinFile", "config/ezz-skin/skin.png");
        } catch (Exception e) {
            System.err.println("[EzzSkinMod] Failed to parse config file: " + e.getMessage());
        }

        return config;
    }

    private static String parseStringField(String json, String key, String defaultVal) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultVal;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon == -1) return defaultVal;
        int quoteStart = json.indexOf('"', colon);
        if (quoteStart == -1) return defaultVal;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) return defaultVal;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private static boolean parseBooleanField(String json, String key, boolean defaultVal) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultVal;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon == -1) return defaultVal;
        String rest = json.substring(colon + 1).trim();
        if (rest.startsWith("true")) return true;
        if (rest.startsWith("false")) return false;
        return defaultVal;
    }
}
