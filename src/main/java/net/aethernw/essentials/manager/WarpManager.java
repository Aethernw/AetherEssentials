package net.aethernw.essentials.manager;

import net.aethernw.essentials.AetherEssentials;
import net.aethernw.essentials.model.WarpLocation;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WarpManager {

    private final AetherEssentials plugin;
    private final ConcurrentHashMap<String, WarpLocation> warps = new ConcurrentHashMap<>();
    private volatile List<String> warpNames;

    public WarpManager(AetherEssentials plugin) {
        this.plugin = plugin;
    }

    private void invalidateWarpNames() {
        warpNames = null;
    }

    public void loadWarps() {
        plugin.getDatabaseManager().loadWarps(new Consumer<List<WarpLocation>>() {
            @Override
            public void accept(List<WarpLocation> loaded) {
                for (WarpLocation warp : loaded) {
                    warps.put(warp.getName(), warp);
                }
                invalidateWarpNames();
                plugin.getLogger().info(loaded.size() + " warp yüklendi.");
            }
        });
    }

    public void setWarp(String name, Location location, final Runnable onFail) {
        WarpLocation warp = WarpLocation.fromLocation(name.toLowerCase(), location);
        warps.put(warp.getName(), warp);
        invalidateWarpNames();
        plugin.getDatabaseManager().saveWarp(warp, onFail);
    }

    public boolean removeWarp(String name, final Runnable onFail) {
        final String key = name.toLowerCase();
        if (warps.remove(key) == null) {
            return false;
        }
        invalidateWarpNames();
        plugin.getDatabaseManager().deleteWarp(key, onFail);
        return true;
    }

    public WarpLocation getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public List<String> getWarpNames() {
        List<String> names = warpNames;
        if (names == null) {
            List<String> sorted = new ArrayList<>(warps.keySet());
            Collections.sort(sorted);
            names = Collections.unmodifiableList(sorted);
            warpNames = names;
        }
        return names;
    }
}
