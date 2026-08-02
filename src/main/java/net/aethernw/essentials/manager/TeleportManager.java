package net.aethernw.essentials.manager;

import net.aethernw.essentials.AetherEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TeleportManager implements Listener {

    private static final String BYPASS_PERMISSION = "aether.teleport.bypass";

    private static final String[] COUNTDOWN_COLORS = new String[21];

    static {
        for (int i = 0; i < COUNTDOWN_COLORS.length; i++) {
            COUNTDOWN_COLORS[i] = countdownColor(1.0 + i * 0.1);
        }
    }

    private static String countdownColor(double value) {
        int red;
        int green;
        if (value >= 2.0) {
            red = (int) (255.0 - 170.0 * (value - 2.0));
            green = 255;
        } else {
            red = 255;
            green = (int) (85.0 + 170.0 * (value - 1.0));
        }
        return hexColor(red, green, 85);
    }

    private static String hexColor(int red, int green, int blue) {
        StringBuilder builder = new StringBuilder("§x");
        String hex = String.format("%02x%02x%02x", red, green, blue);
        for (int i = 0; i < hex.length(); i++) {
            builder.append('§').append(hex.charAt(i));
        }
        return builder.toString();
    }

    private final AetherEssentials plugin;
    private final ConcurrentHashMap<UUID, BukkitTask> pending = new ConcurrentHashMap<>();

    public TeleportManager(AetherEssentials plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, Location location, final Sound arrivalSound, final String message) {
        int seconds = plugin.getConfigManager().getTeleportDelaySeconds();
        if (!plugin.getConfigManager().isTeleportEnabled()
                || seconds <= 0
                || player.hasPermission(BYPASS_PERMISSION)) {
            teleportNow(player, location, arrivalSound, message);
            return;
        }
        cancelPending(player);
        player.sendMessage(plugin.getConfigManager().message("teleport-delay").replace("{seconds}", String.valueOf(seconds)));
        final int[] remainingTenths = {seconds * 10};
        final String[] barTexts = buildBarTexts(seconds, plugin.getConfigManager().message("teleport-actionbar"));
        player.sendActionBar(barTexts[remainingTenths[0]]);
        playCountdownSound(player, seconds);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                remainingTenths[0]--;
                if (remainingTenths[0] <= 0) {
                    pending.remove(player.getUniqueId());
                    player.sendActionBar("");
                    if (player.isOnline()) {
                        teleportNow(player, location, arrivalSound, message);
                    }
                    cancel();
                    return;
                }
                if (remainingTenths[0] % 10 == 0) {
                    playCountdownSound(player, remainingTenths[0] / 10);
                }
                player.sendActionBar(barTexts[remainingTenths[0]]);
            }
        }.runTaskTimer(plugin, 2L, 2L);
        pending.put(player.getUniqueId(), task);
    }

    public void shutdown() {
        for (Map.Entry<UUID, BukkitTask> entry : pending.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.sendActionBar("");
            }
            entry.getValue().cancel();
        }
        pending.clear();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!pending.containsKey(player.getUniqueId()) || !plugin.getConfigManager().isCancelOnMove()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            cancelAndNotify(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (!pending.containsKey(player.getUniqueId()) || !plugin.getConfigManager().isCancelOnDamage()) {
            return;
        }
        cancelAndNotify(player);
    }

    private void cancelAndNotify(Player player) {
        cancelPending(player);
        player.sendMessage(plugin.getConfigManager().message("teleport-cancelled"));
    }

    private void cancelPending(Player player) {
        BukkitTask task = pending.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendActionBar("");
        }
    }

    private static int colorIndex(int tenths) {
        int index = tenths - 10;
        if (index < 0) {
            index = 0;
        }
        if (index > 20) {
            index = 20;
        }
        return index;
    }

    private static String[] buildBarTexts(int seconds, String template) {
        String[] texts = new String[seconds * 10 + 1];
        for (int i = 0; i < texts.length; i++) {
            texts[i] = COUNTDOWN_COLORS[colorIndex(i)] + template.replace("{seconds}", (i / 10) + "." + (i % 10));
        }
        return texts;
    }

    private void playCountdownSound(Player player, int remaining) {
        switch (remaining) {
            case 1:
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f);
                break;
            case 2:
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);
                break;
            case 3:
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.8f);
                break;
        }
    }

    private void teleportNow(Player player, Location location, final Sound arrivalSound, final String message) {
        player.teleportAsync(location).thenAccept(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean success) {
                if (success) {
                    player.playSound(player.getLocation(), arrivalSound, 0.5f, 1.0f);
                    player.sendMessage(message);
                }
            }
        });
    }
}
