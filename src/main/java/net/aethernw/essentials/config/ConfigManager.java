package net.aethernw.essentials.config;

import net.aethernw.essentials.AetherEssentials;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {

    private final AetherEssentials plugin;
    private final FileConfiguration config;
    private final FileConfiguration messages;
    private final ConcurrentHashMap<String, String> messageCache = new ConcurrentHashMap<>();

    public ConfigManager(AetherEssentials plugin) {
        this.plugin = plugin;
        plugin.saveResource("messages.yml", false);
        this.config = plugin.getConfig();
        this.messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        for (String key : messages.getKeys(false)) {
            messageCache.put(key, ChatColor.translateAlternateColorCodes('&', messages.getString(key, "")));
        }
    }

    public String message(String key) {
        String value = messageCache.get(key);
        if (value == null) {
            return ChatColor.translateAlternateColorCodes('&', "&cBilinmeyen mesaj: " + key);
        }
        return value;
    }

    public Location getSpawnLocation() {
        return config.getLocation("spawn");
    }

    public boolean isTeleportEnabled() {
        return config.getBoolean("teleport.enabled", true);
    }

    public int getTeleportDelaySeconds() {
        return config.getInt("teleport.delay-seconds", 3);
    }

    public boolean isCancelOnMove() {
        return config.getBoolean("teleport.cancel-on-move", true);
    }

    public boolean isCancelOnDamage() {
        return config.getBoolean("teleport.cancel-on-damage", true);
    }

    public void setSpawnLocation(Location location) {
        config.set("spawn", location);
        plugin.saveConfig();
    }
}
