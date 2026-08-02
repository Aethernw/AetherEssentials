package net.aethernw.essentials.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.aethernw.essentials.AetherEssentials;
import net.aethernw.essentials.model.WarpLocation;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DatabaseManager {

    private final AetherEssentials plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(AetherEssentials plugin) {
        this.plugin = plugin;
        initPool();
    }

    private void initPool() {
        try {
            FileConfiguration config = plugin.getConfig();
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl("jdbc:mysql://" + config.getString("database.host", "localhost") + ":"
                    + config.getInt("database.port", 3306) + "/"
                    + config.getString("database.database", "aether_essentials")
                    + "?useSSL=false&serverTimezone=UTC");
            hikari.setUsername(config.getString("database.username", "root"));
            hikari.setPassword(config.getString("database.password", ""));
            hikari.setMaximumPoolSize(config.getInt("database.pool-size", 10));
            hikari.setConnectionTimeout(config.getLong("database.connection-timeout", 5000L));
            hikari.setInitializationFailTimeout(0);
            dataSource = new HikariDataSource(hikari);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL bağlantı havuzu oluşturulamadı!", e);
        }
    }

    private static final int CREATE_TABLES_RETRIES = 12;

    public void createTables(Runnable done) {
        if (dataSource == null) {
            plugin.getLogger().warning("MySQL bağlantı havuzu hazır değil, tablo oluşturulamadı");
            return;
        }
        createTablesAsync(done, 0);
    }

    private void createTablesAsync(final Runnable done, final int attempt) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS aether_warps ("
                            + "name VARCHAR(64) NOT NULL, "
                            + "world VARCHAR(64) NOT NULL, "
                            + "x DOUBLE NOT NULL, "
                            + "y DOUBLE NOT NULL, "
                            + "z DOUBLE NOT NULL, "
                            + "yaw FLOAT NOT NULL, "
                            + "pitch FLOAT NOT NULL, "
                            + "PRIMARY KEY (name)) DEFAULT CHARSET=utf8mb4");
                    done.run();
                } catch (SQLException e) {
                    if (attempt < CREATE_TABLES_RETRIES - 1) {
                        plugin.getLogger().log(Level.WARNING, "aether_warps tablosu oluşturulamadı, yeniden deneniyor (" + (attempt + 1) + "/" + CREATE_TABLES_RETRIES + ")", e);
                        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
                            @Override
                            public void run() {
                                createTablesAsync(done, attempt + 1);
                            }
                        }, 200L);
                    } else {
                        plugin.getLogger().log(Level.SEVERE, "aether_warps tablosu " + CREATE_TABLES_RETRIES + " denemeden sonra oluşturulamadı, warp sistemi devre dışı", e);
                    }
                }
            }
        });
    }

    public void loadWarps(Consumer<List<WarpLocation>> callback) {
        if (dataSource == null) {
            plugin.getLogger().warning("MySQL bağlantı havuzu hazır değil, warp verileri yüklenemedi");
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    callback.accept(new ArrayList<>());
                }
            });
            return;
        }
        loadWarpsAsync(callback, 0);
    }

    private void loadWarpsAsync(final Consumer<List<WarpLocation>> callback, final int attempt) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                List<WarpLocation> warps = new ArrayList<>();
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("SELECT name, world, x, y, z, yaw, pitch FROM aether_warps");
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        warps.add(new WarpLocation(result.getString("name"), result.getString("world"),
                                result.getDouble("x"), result.getDouble("y"), result.getDouble("z"),
                                result.getFloat("yaw"), result.getFloat("pitch")));
                    }
                } catch (SQLException e) {
                    if (attempt < CREATE_TABLES_RETRIES - 1) {
                        plugin.getLogger().log(Level.WARNING, "Warp verileri yüklenemedi, yeniden deneniyor (" + (attempt + 1) + "/" + CREATE_TABLES_RETRIES + ")", e);
                        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
                            @Override
                            public void run() {
                                loadWarpsAsync(callback, attempt + 1);
                            }
                        }, 200L);
                    } else {
                        plugin.getLogger().log(Level.SEVERE, "Warp verileri " + CREATE_TABLES_RETRIES + " denemeden sonra yüklenemedi", e);
                    }
                    return;
                }
                final List<WarpLocation> loaded = warps;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        callback.accept(loaded);
                    }
                });
            }
        });
    }

    public void saveWarp(WarpLocation warp, final Runnable onFail) {
        if (dataSource == null) {
            plugin.getLogger().warning("MySQL bağlantı havuzu hazır değil, warp kaydedilemedi: " + warp.getName());
            runOnMainThread(onFail);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("INSERT INTO aether_warps (name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch)")) {
                    statement.setString(1, warp.getName());
                    statement.setString(2, warp.getWorld());
                    statement.setDouble(3, warp.getX());
                    statement.setDouble(4, warp.getY());
                    statement.setDouble(5, warp.getZ());
                    statement.setFloat(6, warp.getYaw());
                    statement.setFloat(7, warp.getPitch());
                    statement.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Warp kaydedilemedi: " + warp.getName(), e);
                    runOnMainThread(onFail);
                }
            }
        });
    }

    public void deleteWarp(String name, final Runnable onFail) {
        if (dataSource == null) {
            plugin.getLogger().warning("MySQL bağlantı havuzu hazır değil, warp silinemedi: " + name);
            runOnMainThread(onFail);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement("DELETE FROM aether_warps WHERE name = ?")) {
                    statement.setString(1, name);
                    statement.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Warp silinemedi: " + name, e);
                    runOnMainThread(onFail);
                }
            }
        });
    }

    private void runOnMainThread(final Runnable onFail) {
        if (onFail == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, onFail);
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
