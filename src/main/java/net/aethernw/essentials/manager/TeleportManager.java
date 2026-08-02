package net.aethernw.essentials.manager;

import net.aethernw.essentials.AetherEssentials;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TeleportManager implements Listener {

    private static final String BYPASS_PERMISSION = "aether.teleport.bypass";

    private final AetherEssentials plugin;
    private final ConcurrentHashMap<UUID, BukkitTask> pending = new ConcurrentHashMap<>();

    public TeleportManager(AetherEssentials plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, Location location, final String message) {
        if (!plugin.getConfigManager().isTeleportEnabled()
                || plugin.getConfigManager().getTeleportDelaySeconds() <= 0
                || player.hasPermission(BYPASS_PERMISSION)) {
            teleportNow(player, location, message);
            return;
        }
        cancelPending(player);
        int seconds = plugin.getConfigManager().getTeleportDelaySeconds();
        player.sendMessage(plugin.getConfigManager().message("teleport-delay").replace("{seconds}", String.valueOf(seconds)));
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                pending.remove(player.getUniqueId());
                if (player.isOnline()) {
                    teleportNow(player, location, message);
                }
            }
        }.runTaskLater(plugin, seconds * 20L);
        pending.put(player.getUniqueId(), task);
    }

    public void cancel(Player player) {
        cancelPending(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfigManager().isCancelOnMove() || !pending.containsKey(player.getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            cancelAndNotify(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!plugin.getConfigManager().isCancelOnDamage() || !(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (pending.containsKey(player.getUniqueId())) {
            cancelAndNotify(player);
        }
    }

    private void cancelAndNotify(Player player) {
        cancelPending(player);
        player.sendMessage(plugin.getConfigManager().message("teleport-cancelled"));
    }

    private void cancelPending(Player player) {
        BukkitTask task = pending.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void teleportNow(Player player, Location location, final String message) {
        player.teleportAsync(location).thenAccept(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean success) {
                if (success) {
                    player.sendMessage(message);
                }
            }
        });
    }
}
