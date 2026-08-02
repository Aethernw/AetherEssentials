package net.aethernw.essentials.manager;

import net.aethernw.essentials.AetherEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TpaManager {

    private static final long TIMEOUT_MILLIS = 30000L;

    private final AetherEssentials plugin;
    private final ConcurrentHashMap<UUID, TpaRequest> requests = new ConcurrentHashMap<>();
    private BukkitTask sweepTask;

    public TpaManager(AetherEssentials plugin) {
        this.plugin = plugin;
    }

    public boolean request(Player sender, Player target) {
        TpaRequest existing = requests.get(target.getUniqueId());
        if (existing != null && !existing.isExpired()) {
            sender.sendMessage(plugin.getConfigManager().message("tpa-already"));
            return false;
        }
        requests.put(target.getUniqueId(), new TpaRequest(sender.getUniqueId(), sender.getName(), System.currentTimeMillis() + TIMEOUT_MILLIS));
        ensureSweep();
        return true;
    }

    private void ensureSweep() {
        if (sweepTask != null && !sweepTask.isCancelled()) {
            return;
        }
        sweepTask = new BukkitRunnable() {
            @Override
            public void run() {
                expireStale();
                if (requests.isEmpty()) {
                    sweepTask.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public TpaRequest pending(UUID target) {
        TpaRequest request = requests.get(target);
        if (request == null || request.isExpired()) {
            return null;
        }
        return request;
    }

    public TpaRequest pending(UUID target, String senderName) {
        TpaRequest request = pending(target);
        if (request == null) {
            return null;
        }
        Player sender = Bukkit.getPlayer(request.getSender());
        if (sender == null || !sender.getName().equalsIgnoreCase(senderName)) {
            return null;
        }
        return request;
    }

    public void remove(UUID target) {
        requests.remove(target);
    }

    public List<String> getPendingSenderNames(UUID target) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<UUID, TpaRequest> entry : requests.entrySet()) {
            if (entry.getKey().equals(target) && !entry.getValue().isExpired()) {
                names.add(entry.getValue().getSenderName());
            }
        }
        return names;
    }

    public int cancelOutgoing(UUID sender) {
        int count = 0;
        Iterator<Map.Entry<UUID, TpaRequest>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TpaRequest> entry = iterator.next();
            if (entry.getValue().getSender().equals(sender)) {
                iterator.remove();
                count++;
            }
        }
        return count;
    }

    private void expireStale() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, TpaRequest>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TpaRequest> entry = iterator.next();
            TpaRequest request = entry.getValue();
            if (now >= request.getExpiresAt()) {
                iterator.remove();
                Player sender = Bukkit.getPlayer(request.getSender());
                if (sender != null) {
                    sender.sendMessage(plugin.getConfigManager().message("tpa-expired"));
                }
            }
        }
    }

    public static class TpaRequest {

        private final UUID sender;
        private final String senderName;
        private final long expiresAt;

        public TpaRequest(UUID sender, String senderName, long expiresAt) {
            this.sender = sender;
            this.senderName = senderName;
            this.expiresAt = expiresAt;
        }

        public UUID getSender() {
            return sender;
        }

        public String getSenderName() {
            return senderName;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
