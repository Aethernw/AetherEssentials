package net.aethernw.essentials.manager;

import net.aethernw.essentials.AetherEssentials;
import net.aethernw.essentials.model.WarpLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WarpManager {

    private final AetherEssentials plugin;
    private final ConcurrentHashMap<String, WarpLocation> warps = new ConcurrentHashMap<>();

    public WarpManager(AetherEssentials plugin) {
        this.plugin = plugin;
    }

    public void loadWarps() {
        plugin.getDatabaseManager().loadWarps(new Consumer<List<WarpLocation>>() {
            @Override
            public void accept(List<WarpLocation> loaded) {
                for (WarpLocation warp : loaded) {
                    warps.put(warp.getName(), warp);
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                    @Override
                    public void run() {
                        plugin.getRedisManager().cacheWarps(loaded);
                    }
                });
                plugin.getLogger().info(loaded.size() + " warp yüklendi.");
            }
        });
    }

    public void setWarp(String name, Location location) {
        WarpLocation warp = WarpLocation.fromLocation(name.toLowerCase(), location);
        warps.put(warp.getName(), warp);
        plugin.getDatabaseManager().saveWarp(warp);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.getRedisManager().cacheWarp(warp);
            }
        });
    }

    public boolean removeWarp(String name) {
        final String key = name.toLowerCase();
        if (warps.remove(key) == null) {
            return false;
        }
        plugin.getDatabaseManager().deleteWarp(key);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.getRedisManager().removeWarp(key);
            }
        });
        return true;
    }

    public WarpLocation getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public List<String> getWarpNames() {
        List<String> names = new ArrayList<>(warps.keySet());
        Collections.sort(names);
        return names;
    }
}
