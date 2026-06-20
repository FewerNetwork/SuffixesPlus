package me.zombieman.dev.suffixesplus.commands;

import me.zombieman.dev.suffixesplus.SuffixesPlus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SuffixAdminCmd implements CommandExecutor, TabCompleter {

    private final SuffixesPlus plugin;

    public SuffixAdminCmd(SuffixesPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("suffixsplus.command.suffixadmin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            sender.sendMessage(plugin.getMessageManager().get("suffixadmin-reload-success"));
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) {
            sender.sendMessage(plugin.getMessageManager().get("suffixadmin-usage"));
            return false;
        }

        String playerName = args[1];
        String type = args[2].toLowerCase();

        if (!type.equals("all") && !type.equals("purchasable") && !type.equals("reset")) {
            sender.sendMessage(plugin.getMessageManager().get("suffixadmin-usage"));
            return false;
        }

        AtomicBoolean playerExists = new AtomicBoolean(true);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (plugin.getDatabase().getUuidByUsername(playerName) == null && Bukkit.getPlayer(playerName) == null) {
                    sender.sendMessage(plugin.getMessageManager().get("suffixadmin-unknown-player", "%player%", playerName));
                    playerExists.set(false);
                } else {
                    plugin.getPurchasedManager().givePurchasedRole(sender, playerName, type);
                }
            } catch (SQLException e) {
                sender.sendMessage(plugin.getMessageManager().get("suffixadmin-db-error"));
                e.printStackTrace();
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player player)) return null;
        if (!player.hasPermission("suffixsplus.command.suffixadmin")) return null;

        if (args.length == 1) {
            completions.add("set");
            completions.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                completions.add(target.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            completions.add("all");
            completions.add("purchasable");
            completions.add("reset");
        }

        String lastArg = args[args.length - 1];
        return completions.stream().filter(s -> s.startsWith(lastArg)).collect(Collectors.toList());
    }
}