package me.zombieman.dev.suffixesplus.commands;

import me.zombieman.dev.suffixesplus.SuffixesPlus;
import me.zombieman.dev.suffixesplus.utils.ChatUtil;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;

public class SuffixesCmd implements CommandExecutor {

    private final SuffixesPlus plugin;

    public SuffixesCmd(SuffixesPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (!player.hasPermission("suffixsplus.command.suffix")) {
            player.sendMessage(plugin.getMessageManager().get("no-permission"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        if (plugin.getLuckPermsHook().getAllSuffixes().isEmpty()) {
            player.sendMessage(plugin.getMessageManager().get("suffixes-no-suffixes"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        try {
            String configSuffix = plugin.getConfig().getString("suffix.prefix", "suffix_");

            if (!plugin.getSuffixDatabase().getAllSuffixes().isEmpty()) {
                player.sendMessage(plugin.getMessageManager().get("suffixes-header-nonpurchasable"));

                for (String s : plugin.getSuffixDatabase().getAllSuffixes()) {
                    String color = ChatColor.translateAlternateColorCodes('&',
                            plugin.getLuckPermsHook().getGroupSuffixColor(configSuffix + s.replace(configSuffix, "")));
                    player.sendMessage(plugin.getMessageManager().get("suffixes-entry", "%suffix%", color));
                }
            }

            player.sendMessage(plugin.getMessageManager().get("suffixes-header-purchasable"));

            for (String s : plugin.getLuckPermsHook().getAllSuffixes()) {
                s = configSuffix + s.replace(configSuffix, "");
                if (!plugin.getSuffixDatabase().getAllSuffixes().contains(s.replace(configSuffix, ""))) {
                    String color = ChatColor.translateAlternateColorCodes('&',
                            plugin.getLuckPermsHook().getGroupSuffixColor(s));
                    player.sendMessage(plugin.getMessageManager().get("suffixes-entry", "%suffix%", color));
                }
            }

            player.sendMessage(plugin.getMessageManager().get("suffixes-footer"));

        } catch (SQLException e) {
            player.sendMessage(plugin.getMessageManager().get("db-error"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            throw new RuntimeException(e);
        }

        return true;
    }
}