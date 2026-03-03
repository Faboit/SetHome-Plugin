package com.sethome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SetHomePlugin extends JavaPlugin implements TabExecutor {

    private File homesDir;
    private File configFile;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private int cooldownSeconds = 3;

    @Override
    public void onEnable() {
        // Create data folder and homes folder
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        homesDir = new File(getDataFolder(), "homes");
        if (!homesDir.exists()) homesDir.mkdirs();

        // Create mainconfig.yml with comments if it doesn't exist
        configFile = new File(getDataFolder(), "mainconfig.yml");
        if (!configFile.exists()) {
            try {
                String sample = 
"# SetHome main configuration file\n"
+ "# cooldown_seconds: number of seconds between /home uses per-player (default 3)\n"
+ "# homes_folder: subfolder inside the plugin folder where player home files are stored\n"
+ "# note: do not use absolute paths here - keep defaults unless you know what you are doing\n"
+ "cooldown_seconds: 3\n"
+ "homes_folder: homes\n"
+ "\n"
+ "# Message templates use Minecraft color codes, e.g. &b and &3 (user requested).\n"
+ "# {home} will be replaced with the home name, {x},{y},{z},{world} with coordinates.\n"
+ "messages:\n"
+ "  set: "&bHome &3{home} &bset at &3{world} &b({x}, {y}, {z})"\n"
+ "  teleport: "&bTeleported to home &3{home}&b (world: &3{world}&b)"\n"
+ "  not_found: "&bNo home &3{home} &bfound."\n"
+ "  cooldown: "&bYou must wait &3{time}s &bbefore using that again."\n";
                configFile.write_text(sample)
            ;
            } catch (Exception e) {
                getLogger().severe("Failed to create mainconfig.yml: " + e.getMessage());
            }
        }

        // load config to set cooldown value
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        cooldownSeconds = cfg.getInt("cooldown_seconds", 3);

        // Register commands
        this.getCommand("sethome").setExecutor(this);
        this.getCommand("sethome").setTabCompleter(this);
        this.getCommand("home").setExecutor(this);
        this.getCommand("home").setTabCompleter(this);

        getLogger().info("SetHomePlugin enabled. Homes folder: " + homesDir.getAbsolutePath());
    }

    @Override
    public void onDisable() {
        getLogger().info("SetHomePlugin disabled.");
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private File playerFile(UUID uuid) {
        return new File(homesDir, uuid.toString() + ".yml");
    }

    private void saveHome(UUID uuid, String name, Location loc) throws IOException {
        File f = playerFile(uuid);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        String base = "Homes." + name + ".";
        cfg.set(base + "X", loc.getX());
        cfg.set(base + "Y", loc.getY());
        cfg.set(base + "Z", loc.getZ());
        cfg.set(base + "Yaw", loc.getYaw());
        cfg.set(base + "Pitch", loc.getPitch());
        cfg.set(base + "World", loc.getWorld().getName());
        cfg.save(f);
    }

    private Location loadHome(UUID uuid, String name) {
        File f = playerFile(uuid);
        if (!f.exists()) return null;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        String base = "Homes." + name + ".";
        if (!cfg.contains(base + "X")) return null;
        double x = cfg.getDouble(base + "X");
        double y = cfg.getDouble(base + "Y");
        double z = cfg.getDouble(base + "Z");
        float yaw = (float) cfg.getDouble(base + "Yaw");
        float pitch = (float) cfg.getDouble(base + "Pitch");
        String worldName = cfg.getString(base + "World", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color("&bOnly players can use this command." ));
            return true;
        }
        Player p = (Player) sender;
        if (command.getName().equalsIgnoreCase("sethome")) {
            String name = args.length > 0 ? args[0].toLowerCase() : "main";
            Location loc = p.getLocation();
            try {
                saveHome(p.getUniqueId(), name, loc);
                // Use message template from config if available
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
                String msg = cfg.getString("messages.set", "&bHome &3{home} &bset.");
                msg = msg.replace("{home}", name)
                         .replace("{x}", String.format("%.2f", loc.getX()))
                         .replace("{y}", String.format("%.2f", loc.getY()))
                         .replace("{z}", String.format("%.2f", loc.getZ()))
                         .replace("{world}", loc.getWorld().getName());
                p.sendMessage(color(msg));
            } catch (IOException e) {
                p.sendMessage(color("&bFailed to save home &3" + name + "&b: &3" + e.getMessage()));
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("home")) {
            String name = args.length > 0 ? args[0].toLowerCase() : "main";
            // cooldown check
            long now = System.currentTimeMillis();
            UUID id = p.getUniqueId();
            if (cooldowns.containsKey(id)) {
                long last = cooldowns.get(id);
                long diff = now - last;
                long must = cooldownSeconds * 1000L;
                if (diff < must) {
                    long remain = (must - diff + 999) / 1000;
                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
                    String tmpl = cfg.getString("messages.cooldown", "&bYou must wait &3{time}s &bbefore using that again.");
                    p.sendMessage(color(tmpl.replace("{time}", Long.toString(remain))));
                    return true;
                }
            }

            Location dest = loadHome(p.getUniqueId(), name);
            if (dest == null) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
                String tmpl = cfg.getString("messages.not_found", "&bNo home &3{home} &bfound.");
                p.sendMessage(color(tmpl.replace("{home}", name)));
                return true;
            }

            p.teleport(dest);
            cooldowns.put(p.getUniqueId(), now);

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
            String tmpl = cfg.getString("messages.teleport", "&bTeleported to home &3{home}&b.");
            tmpl = tmpl.replace("{home}", name).replace("{world}", dest.getWorld().getName());
            p.sendMessage(color(tmpl));
            return true;
        }
        return false;
    }

    // Tab completion suggests "main" and other saved home names
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player p = (Player) sender;
        if (args.length == 1) {
            File f = playerFile(p.getUniqueId());
            if (!f.exists()) return Arrays.asList("main").stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            if (!cfg.contains("Homes")) return Arrays.asList("main").stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            Set<String> keys = cfg.getConfigurationSection("Homes").getKeys(false);
            return keys.stream().filter(k -> k.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
