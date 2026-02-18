package _p3c.SpigotPlugins.minecraftManhunt;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class minecraftManhunt extends JavaPlugin implements Listener, org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {

    // Core manhunt data
    private UUID speedrunner = null; // only one allowed
    private final Set<UUID> hunters = new HashSet<>();
    private final Map<UUID, Location> lastKnown = new HashMap<>();
    private NamespacedKey compassKey;

    // config
    private boolean resetOnNoTrack = true;

    // Hitmen mode fields
    private boolean hitmenMode = false;
    private long hitmenTimeMillis = 0L; // countdown milliseconds
    private boolean timerReady = false; // survivalist pressed /survivalist-ready
    private boolean timerRunning = false;
    private UUID survivalist = null; // same as speedrunner when set

    private long timerStartEpoch = 0L; // System.currentTimeMillis() when started
    private BukkitTask displayTask = null;

    @Override
    public void onEnable() {
        compassKey = new NamespacedKey(this, "manhunt_compass");
        getServer().getPluginManager().registerEvents(this, this);

        // Register commands
        Objects.requireNonNull(getCommand("speedrunner")).setExecutor(this);
        Objects.requireNonNull(getCommand("speedrunner")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("hunter")).setExecutor(this);
        Objects.requireNonNull(getCommand("hunter")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("compass")).setExecutor(this);
        Objects.requireNonNull(getCommand("compass")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("hitmen-mode")).setExecutor(this);
        Objects.requireNonNull(getCommand("hitmen-mode")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("survivalist-ready")).setExecutor(this);
        Objects.requireNonNull(getCommand("survivalist-ready")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("Minecraft-Manhunt")).setExecutor(this);
        Objects.requireNonNull(getCommand("Minecraft-Manhunt")).setTabCompleter(this);

        saveDefaultConfig();
        resetOnNoTrack = getConfig().getBoolean("hunter-compass-reset-when-no-players-to-track", true);

        getLogger().info("minecraftManhunt plugin enabled");
    }

    @Override
    public void onDisable() {
        if (displayTask != null) displayTask.cancel();
        getLogger().info("minecraftManhunt plugin disabled");
    }

    /* ---------- Command dispatcher ---------- */
    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "speedrunner":
                return handleSpeedrunnerCommand(sender, args);
            case "hunter":
                return handleHunterCommand(sender, args);
            case "compass":
                return handleCompassCommand(sender);
            case "hitmen-mode":
                return handleHitmenMode(sender, args);
            case "survivalist-ready":
                return handleSurvivalistReady(sender);
            case "minecraft-manhunt":
                return handleMinecraftManhunt(sender, args);
            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        switch (cmd) {
            case "hitmen-mode":
                if (args.length == 1) completions.addAll(Arrays.asList("ON", "OFF", "RESET"));
                break;
            case "minecraft-manhunt":
                if (args.length == 1) completions.addAll(Arrays.asList("reload", "version"));
                break;
            case "speedrunner":
            case "hunter":
                if (args.length == 1) completions.addAll(Arrays.asList("add", "remove"));
                else if (args.length == 2) for (Player p2 : Bukkit.getOnlinePlayers()) completions.add(p2.getName());
                break;
            default:
                break;
        }
        return completions;
    }

    /* ---------- Compass command ---------- */
    private boolean handleCompassCommand(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player p = (Player) sender;
        if (!hunters.contains(p.getUniqueId())) {
            p.sendMessage(colorMessage(ChatColor.RED, "Only hunters can use this command!"));
            return true;
        }
        if (speedrunner == null) {
            p.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
            return true;
        }
        Player target = Bukkit.getPlayer(speedrunner);
        if (target == null) {
            p.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
            return true;
        }
        p.sendMessage(colorMessage(ChatColor.AQUA, "Compass pointing to " + target.getName()));
        return true;
    }

    /* ---------- Hitmen mode handlers ---------- */
    private boolean handleHitmenMode(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendInvalidHitmenUsage(sender);
            return true;
        }
        String sub = args[0].toUpperCase(Locale.ROOT);
        if (sub.equals("ON")) {
            if (args.length < 2) {
                sendInvalidHitmenUsage(sender);
                return true;
            }
            long seconds;
            try {
                seconds = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                sendInvalidHitmenUsage(sender);
                return true;
            }
            if (seconds <= 0) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Time must be greater than 0 seconds!"));
                return true;
            }
            hitmenMode = true;
            hitmenTimeMillis = seconds * 1000L;
            timerReady = false;
            timerRunning = false;
            survivalist = speedrunner; // assume current speedrunner

            String formatted = formatDurationVerbose(hitmenTimeMillis);
            Bukkit.broadcastMessage(colorMessage(ChatColor.GREEN, "Minecraft Hitmen Mode has been successfully turned on!"));
            Bukkit.broadcastMessage(colorMessage(ChatColor.GREEN, "The survivalist must survive for " + formatted + "!"));

            startOrUpdateDisplayTask();
            return true;
        } else if (sub.equals("OFF")) {
            hitmenMode = false;
            hitmenTimeMillis = 0L;
            timerReady = false;
            timerRunning = false;
            survivalist = null;
            cancelDisplayTask();
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Minecraft Hitmen Mode has been successfully turned off!"));
            return true;
        } else if (sub.equals("RESET")) {
            hitmenMode = false;
            hitmenTimeMillis = 0L;
            timerReady = false;
            timerRunning = false;
            survivalist = null;
            cancelDisplayTask();
            sender.sendMessage(colorMessage(ChatColor.GREEN, "All values have been reset and Hitmen Mode has been turned off."));
            return true;
        }
        sendInvalidHitmenUsage(sender);
        return true;
    }

    private void sendInvalidHitmenUsage(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(colorMessage(ChatColor.RED, "Invalid arguments!"));
        sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /hitmen-mode ON <time in seconds>"));
        sender.sendMessage(colorMessage(ChatColor.YELLOW, "Or: /hitmen-mode OFF"));
        sender.sendMessage(colorMessage(ChatColor.YELLOW, "To reset: /hitmen-mode RESET"));
    }

    private boolean handleSurvivalistReady(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player p = (Player) sender;
        if (!hitmenMode) {
            p.sendMessage(colorMessage(ChatColor.RED, "Minecraft Hitman Mode is off!"));
            return true;
        }
        if (survivalist == null || !survivalist.equals(p.getUniqueId())) {
            p.sendMessage(colorMessage(ChatColor.RED, "You are not the survivalist!"));
            return true;
        }
        timerReady = true;
        applySurvivalistGlow(p);
        p.sendMessage(colorMessage(ChatColor.GREEN, "Survivalist is ready! Timer will be started from any second you move!"));
        return true;
    }

    private boolean handleMinecraftManhunt(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(colorMessage(ChatColor.RED, "Invalid arguments!"));
            sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /Minecraft-Manhunt <arguments>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            sender.sendMessage(colorMessage(ChatColor.AQUA, "Reloading Minecraft Manhunt plugin..."));
            long start = System.nanoTime();
            reloadConfig();
            resetOnNoTrack = getConfig().getBoolean("hunter-compass-reset-when-no-players-to-track", true);
            long end = System.nanoTime();
            double took = (end - start) / 1e9;
            sender.sendMessage(colorMessage(ChatColor.GREEN, String.format("Reloaded Successfully (%.1f seconds)!", took)));
            return true;
        } else if (sub.equals("version")) {
            sender.sendMessage(colorMessage(ChatColor.YELLOW, "Running on Minecraft Version " + getServer().getVersion() + "!"));
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Plugin Version: Minecraft Manhunt " + getDescription().getVersion()));
            sender.sendMessage(colorMessage(ChatColor.YELLOW, "Discord: @P3C, Minecraft Manhunt"));
            return true;
        }
        sender.sendMessage(colorMessage(ChatColor.RED, "Invalid arguments!"));
        sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /Minecraft-Manhunt <arguments>"));
        return true;
    }

    /* ---------- Display / timer tasks ---------- */
    private void startOrUpdateDisplayTask() {
        if (displayTask != null) displayTask.cancel();
        displayTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!hitmenMode) return;
                long remaining;
                if (timerRunning) {
                    long elapsed = System.currentTimeMillis() - timerStartEpoch;
                    remaining = Math.max(0L, hitmenTimeMillis - elapsed);
                } else {
                    remaining = hitmenTimeMillis;
                }
                String formatted = formatTimer(remaining);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(formatted));
                }
                if (timerRunning && remaining <= 0) {
                    timerRunning = false;
                    hitmenMode = false;
                    handleSurvivalistWin();
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L); // every tick
    }

    private void cancelDisplayTask() {
        if (displayTask != null) displayTask.cancel();
        displayTask = null;
        for (Player p : Bukkit.getOnlinePlayers()) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    private void startTimer() {
        if (!hitmenMode || !timerReady || timerRunning) return;
        timerRunning = true;
        timerStartEpoch = System.currentTimeMillis();
    }

    private void handleSurvivalistWin() {
        Bukkit.broadcastMessage(colorMessage(ChatColor.GREEN, "The Survivalist won! The Hitmen ran out of time and lost!"));
        if (survivalist != null) {
            Player s = Bukkit.getPlayer(survivalist);
            if (s != null) s.sendMessage(colorMessage(ChatColor.GREEN, "You have won!"));
        }
        for (UUID id : hunters) {
            Player h = Bukkit.getPlayer(id);
            if (h != null) h.sendMessage(colorMessage(ChatColor.RED, "You have run out of time and lost!"));
        }
        hitmenMode = false;
        timerReady = false;
        timerRunning = false;
        survivalist = null;
        cancelDisplayTask();
    }

    private void handleHitmenWin(UUID survId) {
        Bukkit.broadcastMessage(colorMessage(ChatColor.RED, "The Hitmen won! The Survivalist died and lost!"));
        for (UUID id : hunters) {
            Player h = Bukkit.getPlayer(id);
            if (h != null) h.sendMessage(colorMessage(ChatColor.GREEN, "You have won!"));
        }
        Player s = Bukkit.getPlayer(survId);
        if (s != null) s.sendMessage(colorMessage(ChatColor.GREEN, "You died and lost!"));

        hitmenMode = false;
        timerReady = false;
        timerRunning = false;
        survivalist = null;
        cancelDisplayTask();
    }

    /* ---------- Events ---------- */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        ItemStack item = event.getItem();
        if (!isManhuntCompass(item)) return;

        Player player = event.getPlayer();

        if (speedrunner != null && speedrunner.equals(player.getUniqueId())) {
            player.sendMessage(colorMessage(ChatColor.RED, "You are a speedrunner, you can't use this!"));
            event.setCancelled(true);
            return;
        }

        if (!hunters.contains(player.getUniqueId())) return;

        switch (event.getAction()) {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
                if (speedrunner == null) {
                    player.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
                    if (resetOnNoTrack) {
                        CompassMeta cm = (CompassMeta) item.getItemMeta();
                        cm.setLodestone(null);
                        cm.setLodestoneTracked(false);
                        item.setItemMeta(cm);
                    }
                    event.setCancelled(true);
                    return;
                }
                Player target = Bukkit.getPlayer(speedrunner);
                if (target == null) {
                    player.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
                    if (resetOnNoTrack) {
                        CompassMeta cm = (CompassMeta) item.getItemMeta();
                        cm.setLodestone(null);
                        cm.setLodestoneTracked(false);
                        item.setItemMeta(cm);
                    }
                    event.setCancelled(true);
                    return;
                }

                World hunterWorld = player.getWorld();
                World targetWorld = target.getWorld();
                if (!hunterWorld.getEnvironment().equals(targetWorld.getEnvironment())) {
                    player.sendMessage(colorMessage(ChatColor.RED, "No players to track!"));
                    if (resetOnNoTrack) {
                        CompassMeta cm = (CompassMeta) item.getItemMeta();
                        cm.setLodestone(null);
                        cm.setLodestoneTracked(false);
                        item.setItemMeta(cm);
                    }
                    event.setCancelled(true);
                    return;
                }

                Location loc = target.getLocation().clone();
                lastKnown.put(player.getUniqueId(), loc);

                CompassMeta cm = (CompassMeta) item.getItemMeta();
                cm.setLodestone(loc);
                cm.setLodestoneTracked(false);
                item.setItemMeta(cm);

                player.sendMessage(colorMessage(ChatColor.GREEN, "Tracking " + target.getName() + "'s last known location."));
                event.setCancelled(true);
                return;
            default:
                return;
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Entity killer = event.getEntity().getKiller();
        UUID dead = event.getEntity().getUniqueId();
        event.getDrops().removeIf(this::isManhuntCompass);

        if (hitmenMode && survivalist != null && survivalist.equals(dead)) {
            handleHitmenWin(dead);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (hunters.contains(p.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> giveHunterCompass(p), 1L);
        }
        if (survivalist != null && survivalist.equals(p.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> applySurvivalistGlow(p), 5L);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (hunters.contains(p.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> giveHunterCompass(p), 10L);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!hitmenMode) return;
        if (!timerReady) return;
        if (timerRunning) return;
        Player p = event.getPlayer();
        if (survivalist == null) return;
        if (!p.getUniqueId().equals(survivalist)) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            startTimer();
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!hitmenMode) return;
        Entity damager = event.getDamager();
        Entity target = event.getEntity();
        if (damager instanceof Player && target instanceof Player) {
            Player d = (Player) damager;
            Player t = (Player) target;
            if (survivalist != null && d.getUniqueId().equals(survivalist) && hunters.contains(t.getUniqueId())) {
                startTimer();
            }
        }
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!hitmenMode) return;
        Player p = event.getPlayer();
        if (survivalist != null && p.getUniqueId().equals(survivalist)) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.MILK_BUCKET) {
                Bukkit.getScheduler().runTaskLater(this, () -> applySurvivalistGlow(p), 2L);
            }
        }
    }

    /* ---------- Utility methods (all present) ---------- */

    private void giveHunterCompass(Player p) {
        ItemStack c = createManhuntCompass();
        p.getInventory().addItem(c);
    }

    private ItemStack createManhuntCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        if (meta == null) return compass;
        meta.setDisplayName(ChatColor.AQUA + "Hunter Compass");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Right click to update last known location.");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        meta.setLodestone(null);
        meta.setLodestoneTracked(false);
        compass.setItemMeta(meta);
        return compass;
    }

    private boolean isManhuntCompass(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte val = meta.getPersistentDataContainer().get(compassKey, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    private String colorMessage(ChatColor color, String message) {
        return color + message;
    }

    private String formatDurationVerbose(long millis) {
        long totalSeconds = millis / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" days, ");
        if (hours > 0 || days > 0) sb.append(hours).append(" hours, ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append(" minutes and ");
        sb.append(seconds).append(" seconds");
        return sb.toString();
    }

    private String formatTimer(long millis) {
        long ms = millis % 1000;
        long totalSeconds = millis / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = (totalSeconds / 3600);
        String hoursStr = Long.toString(hours);
        if (hoursStr.length() < 2) hoursStr = String.format("%02d", hours);
        return String.format("%s:%02d:%02d:%03d", hoursStr, minutes, seconds, ms);
    }

    private void applySurvivalistGlow(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 1, false, false));
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("manhunt_survivalist");
        if (team == null) team = board.registerNewTeam("manhunt_survivalist");
        team.setColor(ChatColor.RED);
        team.addEntry(p.getName());
    }

    /* ---------- Original commands: speedrunner / hunter ---------- */

    private boolean handleSpeedrunnerCommand(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /speedrunner add <playername>, or /speedrunner remove <playername>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String name = args[1];
        if (sub.equals("add")) {
            if (speedrunner != null) {
                sender.sendMessage(colorMessage(ChatColor.RED, "A speedrunner is already set!"));
                return true;
            }
            Player p = Bukkit.getPlayerExact(name);
            if (p == null) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Invalid player!"));
                return true;
            }
            if (hunters.contains(p.getUniqueId())) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Speedrunner and hunter cannot be the same!"));
                return true;
            }
            speedrunner = p.getUniqueId();
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Speedrunner set to " + p.getName()));
            return true;
        } else if (sub.equals("remove")) {
            Player p = Bukkit.getPlayerExact(name);
            if (p == null || speedrunner == null || !speedrunner.equals(p.getUniqueId())) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Invalid player!"));
                return true;
            }
            speedrunner = null;
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Speedrunner removed."));
            return true;
        }
        sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /speedrunner add <playername>, or /speedrunner remove <playername>"));
        return true;
    }

    private boolean handleHunterCommand(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /hunter <playername>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String name = args[1];
        if (sub.equals("add")) {
            Player p = Bukkit.getPlayerExact(name);
            if (p == null) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Invalid player!"));
                return true;
            }
            if (speedrunner != null && speedrunner.equals(p.getUniqueId())) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Speedrunner and hunter cannot be the same!"));
                return true;
            }
            hunters.add(p.getUniqueId());
            giveHunterCompass(p);
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Hunter set to " + p.getName()));
            return true;
        } else if (sub.equals("remove")) {
            Player p = Bukkit.getPlayerExact(name);
            if (p == null) {
                sender.sendMessage(colorMessage(ChatColor.RED, "Invalid player!"));
                return true;
            }
            hunters.remove(p.getUniqueId());
            lastKnown.remove(p.getUniqueId());
            sender.sendMessage(colorMessage(ChatColor.GREEN, "Hunter removed: " + p.getName()));
            return true;
        }
        sender.sendMessage(colorMessage(ChatColor.YELLOW, "Usage: /hunter <playername>"));
        return true;
    }
}
