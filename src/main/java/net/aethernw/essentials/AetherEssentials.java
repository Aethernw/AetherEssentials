package net.aethernw.essentials;

import net.aethernw.essentials.config.ConfigManager;
import net.aethernw.essentials.database.DatabaseManager;
import net.aethernw.essentials.database.RedisManager;
import net.aethernw.essentials.manager.TpaManager;
import net.aethernw.essentials.manager.TpaManager.TpaRequest;
import net.aethernw.essentials.manager.TeleportManager;
import net.aethernw.essentials.manager.WarpManager;
import net.aethernw.essentials.model.WarpLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class AetherEssentials extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final Pattern WARP_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,32}");

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private WarpManager warpManager;
    private TpaManager tpaManager;
    private TeleportManager teleportManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        databaseManager = new DatabaseManager(this);
        redisManager = new RedisManager(this);
        warpManager = new WarpManager(this);
        tpaManager = new TpaManager(this);
        teleportManager = new TeleportManager(this);
        Bukkit.getPluginManager().registerEvents(teleportManager, this);

        String[] allCommands = {"craft", "enderchest", "setwarp", "warp", "warps",
                "delwarp", "spawn", "setspawn", "repair", "feed",
                "tpa", "tpaccept", "tpadeny", "tpacancel"};
        String permissionMessage = configManager.message("no-permission");
        for (String cmdName : allCommands) {
            this.getCommand(cmdName).setExecutor(this);
            this.getCommand(cmdName).setPermissionMessage(permissionMessage);
        }
        this.getCommand("warp").setTabCompleter(this);
        this.getCommand("delwarp").setTabCompleter(this);
        this.getCommand("tpa").setTabCompleter(this);
        this.getCommand("tpaccept").setTabCompleter(this);
        this.getCommand("tpadeny").setTabCompleter(this);

        tpaManager.start();
        databaseManager.createTables(new Runnable() {
            @Override
            public void run() {
                warpManager.loadWarps();
            }
        });
    }

    @Override
    public void onDisable() {
        if (teleportManager != null) {
            teleportManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        if (redisManager != null) {
            redisManager.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "craft":
                craftCommand(sender);
                break;
            case "enderchest":
                enderchestCommand(sender, args);
                break;
            case "setwarp":
                setWarpCommand(sender, args);
                break;
            case "warp":
                warpCommand(sender, args);
                break;
            case "warps":
                warpsCommand(sender);
                break;
            case "delwarp":
                delWarpCommand(sender, args);
                break;
            case "spawn":
                spawnCommand(sender);
                break;
            case "setspawn":
                setSpawnCommand(sender);
                break;
            case "repair":
                repairCommand(sender, args);
                break;
            case "feed":
                feedCommand(sender, args);
                break;
            case "tpa":
                tpaCommand(sender, args);
                break;
            case "tpaccept":
                tpacceptCommand(sender, args);
                break;
            case "tpadeny":
                tpadenyCommand(sender, args);
                break;
            case "tpacancel":
                tpacancelCommand(sender);
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length != 1) {
            return result;
        }
        String name = command.getName().toLowerCase();
        String prefix = args[0].toLowerCase();
        if (name.equals("warp") || name.equals("delwarp")) {
            if (!sender.hasPermission("aether.command." + name)) {
                return result;
            }
            for (String warpName : warpManager.getWarpNames()) {
                if (warpName.startsWith(prefix)) {
                    result.add(warpName);
                }
            }
        } else if (name.equals("tpa") || name.equals("enderchest") || name.equals("feed")) {
            String permission = "aether.command." + name;
            if (name.equals("enderchest") || name.equals("feed")) {
                permission = permission + ".others";
            }
            if (!sender.hasPermission(permission)) {
                return result;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    result.add(online.getName());
                }
            }
        } else if (name.equals("repair")) {
            if (sender.hasPermission("aether.command.repair.all") && "all".startsWith(prefix)) {
                result.add("all");
            }
        } else if (name.equals("tpaccept") || name.equals("tpadeny")) {
            if (!(sender instanceof Player) || !sender.hasPermission("aether.command.tpa")) {
                return result;
            }
            Player player = (Player) sender;
            for (String pendingName : tpaManager.getPendingSenderNames(player.getUniqueId())) {
                if (pendingName.toLowerCase().startsWith(prefix)) {
                    result.add(pendingName);
                }
            }
        }
        return result;
    }

    private void craftCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.craft")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        player.openWorkbench(null, true);
        player.sendMessage(configManager.message("craft-opened"));
    }

    private void enderchestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.enderchest")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        if (args.length == 0) {
            player.openInventory(player.getEnderChest());
            player.sendMessage(configManager.message("ec-opened"));
            return;
        }
        if (!player.hasPermission("aether.command.enderchest.others")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(configManager.message("player-not-found"));
            return;
        }
        if (target.equals(player)) {
            player.openInventory(player.getEnderChest());
            player.sendMessage(configManager.message("ec-opened"));
            return;
        }
        player.openInventory(target.getEnderChest());
        player.sendMessage(configManager.message("ec-opened-other").replace("{player}", target.getName()));
    }

    private void setWarpCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.setwarp")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        if (args.length != 1) {
            player.sendMessage(configManager.message("setwarp-usage"));
            return;
        }
        if (!WARP_NAME_PATTERN.matcher(args[0]).matches()) {
            player.sendMessage(configManager.message("warp-invalid-name"));
            return;
        }
        warpManager.setWarp(args[0], player.getLocation());
        player.sendMessage(configManager.message("warp-set").replace("{warp}", args[0]));
    }

    private void warpCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.warp")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        if (args.length != 1) {
            player.sendMessage(configManager.message("warp-usage"));
            return;
        }
        WarpLocation warp = warpManager.getWarp(args[0]);
        if (warp == null) {
            player.sendMessage(configManager.message("warp-not-found"));
            return;
        }
        Location location = warp.toLocation();
        if (location == null) {
            player.sendMessage(configManager.message("warp-world-unloaded"));
            return;
        }
        teleportManager.request(player, location, Sound.ENTITY_ENDERMAN_TELEPORT, configManager.message("warp-teleported").replace("{warp}", warp.getName()));
    }

    private void warpsCommand(CommandSender sender) {
        if (!sender.hasPermission("aether.command.warps")) {
            sender.sendMessage(configManager.message("no-permission"));
            return;
        }
        List<String> names = warpManager.getWarpNames();
        if (names.isEmpty()) {
            sender.sendMessage(configManager.message("warps-empty"));
            return;
        }
        sender.sendMessage(configManager.message("warps-list").replace("{warps}", String.join(", ", names)));
    }

    private void delWarpCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aether.command.delwarp")) {
            sender.sendMessage(configManager.message("no-permission"));
            return;
        }
        if (args.length != 1) {
            sender.sendMessage(configManager.message("delwarp-usage"));
            return;
        }
        if (!warpManager.removeWarp(args[0])) {
            sender.sendMessage(configManager.message("warp-not-found"));
            return;
        }
        sender.sendMessage(configManager.message("warp-deleted").replace("{warp}", args[0]));
    }

    private void spawnCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.spawn")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        Location spawn = configManager.getSpawnLocation();
        if (spawn == null) {
            player.sendMessage(configManager.message("spawn-not-set"));
            return;
        }
        teleportManager.request(player, spawn, Sound.BLOCK_NOTE_BLOCK_CHIME, configManager.message("spawn-teleported"));
    }

    private void setSpawnCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.setspawn")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        configManager.setSpawnLocation(player.getLocation());
        player.sendMessage(configManager.message("spawn-set"));
    }

    private void repairCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            if (!player.hasPermission("aether.command.repair.all")) {
                player.sendMessage(configManager.message("no-permission"));
                return;
            }
            int count = repairAll(player);
            player.sendMessage(configManager.message("repaired-all").replace("{count}", String.valueOf(count)));
            return;
        }
        if (args.length > 0) {
            player.sendMessage(configManager.message("invalid-usage"));
            return;
        }
        if (!player.hasPermission("aether.command.repair")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !repair(item)) {
            player.sendMessage(configManager.message("repair-invalid"));
        } else {
            player.sendMessage(configManager.message("repaired"));
        }
    }

    private int repairAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (repair(item)) {
                count++;
            }
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (repair(item)) {
                count++;
            }
        }
        if (repair(inventory.getItemInOffHand())) {
            count++;
        }
        return count;
    }

    private boolean repair(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) {
            return false;
        }
        Damageable damageable = (Damageable) meta;
        damageable.setDamage(0);
        item.setItemMeta(meta);
        return true;
    }

    private void feedCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(configManager.message("player-only"));
                return;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("aether.command.feed")) {
                player.sendMessage(configManager.message("no-permission"));
                return;
            }
            feed(player);
            player.sendMessage(configManager.message("fed"));
            return;
        }
        if (!sender.hasPermission("aether.command.feed.others")) {
            sender.sendMessage(configManager.message("no-permission"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(configManager.message("player-not-found"));
            return;
        }
        feed(target);
        sender.sendMessage(configManager.message("fed-other").replace("{player}", target.getName()));
    }

    private void feed(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
    }

    private void tpaCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.tpa")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        if (args.length != 1) {
            player.sendMessage(configManager.message("tpa-usage"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(configManager.message("player-not-found"));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(configManager.message("tpa-self"));
            return;
        }
        if (!tpaManager.request(player, target)) {
            return;
        }
        player.sendMessage(configManager.message("tpa-sent").replace("{player}", target.getName()));
        target.sendMessage(configManager.message("tpa-received").replace("{player}", player.getName()));
    }

    private void tpacceptCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.tpa")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        TpaRequest request;
        if (args.length == 0) {
            request = tpaManager.pending(player.getUniqueId());
        } else {
            request = tpaManager.pending(player.getUniqueId(), args[0]);
        }
        if (request == null) {
            player.sendMessage(configManager.message("tpa-no-request"));
            return;
        }
        tpaManager.remove(request.getTarget());
        Player requester = Bukkit.getPlayer(request.getSender());
        if (requester == null) {
            player.sendMessage(configManager.message("tpa-offline"));
            return;
        }
        teleportManager.request(requester, player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, configManager.message("tpa-accepted-sender").replace("{player}", player.getName()));
        player.sendMessage(configManager.message("tpa-accepted").replace("{player}", requester.getName()));
    }

    private void tpadenyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.tpa")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        TpaRequest request;
        if (args.length == 0) {
            request = tpaManager.pending(player.getUniqueId());
        } else {
            request = tpaManager.pending(player.getUniqueId(), args[0]);
        }
        if (request == null) {
            player.sendMessage(configManager.message("tpa-no-request"));
            return;
        }
        tpaManager.remove(request.getTarget());
        player.sendMessage(configManager.message("tpa-denied").replace("{player}", request.getSenderName()));
        Player requester = Bukkit.getPlayer(request.getSender());
        if (requester != null) {
            requester.sendMessage(configManager.message("tpa-denied-sender").replace("{player}", player.getName()));
        }
    }

    private void tpacancelCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.message("player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aether.command.tpa")) {
            player.sendMessage(configManager.message("no-permission"));
            return;
        }
        int cancelled = tpaManager.cancelOutgoing(player.getUniqueId());
        if (cancelled > 0) {
            player.sendMessage(configManager.message("tpa-cancelled"));
        } else {
            player.sendMessage(configManager.message("tpa-no-request"));
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }
}
