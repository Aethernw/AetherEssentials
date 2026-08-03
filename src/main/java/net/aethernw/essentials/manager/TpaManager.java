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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TpaManager {

    private static final long TIMEOUT_MILLIS = 30000L;

    private final AetherEssentials plugin;
    private final ConcurrentHashMap<UUID, List<TpaRequest>> requestsByTarget = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<UUID>> ignored = new ConcurrentHashMap<>();
    private final Set<UUID> ignoreAll = ConcurrentHashMap.newKeySet();
    private BukkitTask sweepTask;

    public TpaManager(AetherEssentials plugin) {
        this.plugin = plugin;
    }

    public boolean request(Player sender, Player target, TpaRequest.Type type) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(plugin.getConfigManager().message("tpa-self"));
            return false;
        }
        if (isIgnored(target.getUniqueId(), sender.getUniqueId())) {
            sender.sendMessage(plugin.getConfigManager().message("tpa-ignored"));
            return false;
        }
        List<TpaRequest> list = requests(target.getUniqueId());
        for (TpaRequest request : list) {
            if (!request.isExpired() && request.getSender().equals(sender.getUniqueId())) {
                sender.sendMessage(plugin.getConfigManager().message("tpa-already"));
                return false;
            }
        }
        TpaRequest request = new TpaRequest(sender.getUniqueId(), sender.getName(), target.getName(), System.currentTimeMillis() + TIMEOUT_MILLIS, type);
        list.add(request);
        ensureSweep();
        if (type == TpaRequest.Type.TO) {
            sender.sendMessage(plugin.getConfigManager().message("tpa-sent").replace("{player}", target.getName()));
            target.sendMessage(plugin.getConfigManager().message("tpa-received").replace("{player}", sender.getName()));
        } else {
            sender.sendMessage(plugin.getConfigManager().message("tpahere-sent").replace("{player}", target.getName()));
            target.sendMessage(plugin.getConfigManager().message("tpahere-received").replace("{player}", sender.getName()));
        }
        return true;
    }

    public TpaRequest pending(UUID target) {
        for (TpaRequest request : requests(target)) {
            if (!request.isExpired()) {
                return request;
            }
        }
        return null;
    }

    public TpaRequest pending(UUID target, String senderName) {
        for (TpaRequest request : requests(target)) {
            if (!request.isExpired() && request.getSenderName().equalsIgnoreCase(senderName)) {
                return request;
            }
        }
        return null;
    }

    public List<TpaRequest> getPendingRequests(UUID target) {
        List<TpaRequest> pendingRequests = new ArrayList<>();
        for (TpaRequest request : requests(target)) {
            if (!request.isExpired()) {
                pendingRequests.add(request);
            }
        }
        return pendingRequests;
    }

    public List<String> getPendingSenderNames(UUID target) {
        List<String> names = new ArrayList<>();
        for (TpaRequest request : getPendingRequests(target)) {
            names.add(request.getSenderName());
        }
        return names;
    }

    public void remove(UUID target, UUID sender) {
        requests(target).removeIf(request -> request.getSender().equals(sender));
    }

    public int cancelOutgoing(UUID sender) {
        int count = 0;
        for (List<TpaRequest> list : requestsByTarget.values()) {
            Iterator<TpaRequest> iterator = list.iterator();
            while (iterator.hasNext()) {
                TpaRequest request = iterator.next();
                if (request.getSender().equals(sender)) {
                    iterator.remove();
                    count++;
                }
            }
        }
        return count;
    }

    public boolean isIgnored(UUID target, UUID sender) {
        if (ignoreAll.contains(target)) {
            return true;
        }
        Set<UUID> set = ignored.get(target);
        return set != null && set.contains(sender);
    }

    public boolean toggleIgnore(UUID player, UUID target) {
        Set<UUID> set = ignored.computeIfAbsent(player, uuid -> ConcurrentHashMap.newKeySet());
        if (set.contains(target)) {
            set.remove(target);
            return false;
        }
        set.add(target);
        return true;
    }

    public boolean toggleIgnoreAll(UUID player) {
        if (ignoreAll.contains(player)) {
            ignoreAll.remove(player);
            return false;
        }
        ignoreAll.add(player);
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
                if (requestsByTarget.values().stream().allMatch(List::isEmpty)) {
                    sweepTask.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void expireStale() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, List<TpaRequest>>> iterator = requestsByTarget.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, List<TpaRequest>> entry = iterator.next();
            Iterator<TpaRequest> requestIterator = entry.getValue().iterator();
            while (requestIterator.hasNext()) {
                TpaRequest request = requestIterator.next();
                if (now >= request.getExpiresAt()) {
                    requestIterator.remove();
                    Player sender = Bukkit.getPlayer(request.getSender());
                    if (sender != null) {
                        sender.sendMessage(plugin.getConfigManager().message("tpa-expired").replace("{player}", request.getTargetName()));
                    }
                }
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private List<TpaRequest> requests(UUID target) {
        return requestsByTarget.computeIfAbsent(target, uuid -> new ArrayList<>());
    }

    public static class TpaRequest {

        private final UUID sender;
        private final String senderName;
        private final String targetName;
        private final long expiresAt;
        private final Type type;

        public TpaRequest(UUID sender, String senderName, String targetName, long expiresAt, Type type) {
            this.sender = sender;
            this.senderName = senderName;
            this.targetName = targetName;
            this.expiresAt = expiresAt;
            this.type = type;
        }

        public UUID getSender() {
            return sender;
        }

        public String getSenderName() {
            return senderName;
        }

        public String getTargetName() {
            return targetName;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public Type getType() {
            return type;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }

        public enum Type {
            TO, HERE
        }
    }
}