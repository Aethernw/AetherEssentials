package net.aethernw.essentials.database;

import net.aethernw.essentials.AetherEssentials;
import net.aethernw.essentials.model.WarpLocation;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;
import java.util.logging.Level;

public class RedisManager {

    private static final String WARPS_KEY = "aether:warps";

    private final AetherEssentials plugin;
    private JedisPool pool;
    private boolean enabled;

    public RedisManager(AetherEssentials plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("redis.enabled")) {
            return;
        }
        try {
            String password = config.getString("redis.password", "");
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(8);
            pool = new JedisPool(poolConfig, config.getString("redis.host", "localhost"),
                    config.getInt("redis.port", 6379), 2000,
                    password.isEmpty() ? null : password, config.getInt("redis.database", 0));
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }
            enabled = true;
            plugin.getLogger().info("Redis bağlantısı kuruldu.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Redis bağlantısı kurulamadı, cache devre dışı", e);
        }
    }

    public void cacheWarps(List<WarpLocation> warps) {
        if (!enabled) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.del(WARPS_KEY);
            for (WarpLocation warp : warps) {
                jedis.hset(WARPS_KEY, warp.getName(), warp.serialize());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Warp cache güncellenemedi", e);
        }
    }

    public void cacheWarp(WarpLocation warp) {
        if (!enabled) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(WARPS_KEY, warp.getName(), warp.serialize());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Warp cache güncellenemedi", e);
        }
    }

    public void removeWarp(String name) {
        if (!enabled) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.hdel(WARPS_KEY, name);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Warp cache güncellenemedi", e);
        }
    }

    public void shutdown() {
        if (pool != null) {
            pool.close();
        }
    }
}
