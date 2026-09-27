package me.zombieman.dev.suffixesplus.commands;

import me.zombieman.dev.suffixesplus.SuffixesPlus;
import me.zombieman.dev.suffixesplus.utils.ChatUtil;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SuffixCmd implements CommandExecutor, TabCompleter {

    private final SuffixesPlus plugin;

    public SuffixCmd(SuffixesPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (!player.hasPermission("suffixsplus.command.suffix")) {
            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("no-permission")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        // /suffix notification
        if (args.length >= 1 && args[0].equalsIgnoreCase("notification")
                && plugin.getConfig().getBoolean("suffix.notification", true)) {

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    boolean currentlyOn = plugin.getDatabase().getPlayer(player.getUniqueId(), player.getName()).getNotifications();
                    boolean newValue = !currentlyOn;

                    plugin.getDatabase().updateNotification(player.getUniqueId(), newValue);

                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw(
                            newValue ? "suffix-notification-on" : "suffix-notification-off")));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);

                } catch (SQLException e) {
                    e.printStackTrace();
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("db-error")));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            });

            return true;
        }

        // Admin subcommands
        if (args.length >= 1 && player.hasPermission("suffixsplus.command.suffixadmin")) {

            String action = args[0];
            String configSuffix = plugin.getConfig().getString("suffix.prefix", "suffix_");

            switch (action) {

                case "add": {
                    if (args.length < 2) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-add-usage")));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return false;
                    }

                    String suffix = args[1];
                    boolean contains = plugin.getLuckPermsHook().getAllSuffixes()
                            .contains(configSuffix + suffix.replace(configSuffix, ""));

                    if (!contains) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-add-invalid", "%suffix%", suffix)));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return false;
                    }

                    try {
                        if (plugin.getSuffixDatabase().getAllSuffixes().contains(suffix)) {
                            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-add-already-exists")));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            return false;
                        }

                        plugin.getSuffixDatabase().addSuffix(suffix);
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-add-success", "%suffix%", suffix)));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);

                    } catch (SQLException e) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("db-error")));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        throw new RuntimeException(e);
                    }
                    return true;
                }

                case "remove": {
                    if (args.length < 2) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-remove-usage")));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return false;
                    }

                    String suffix = args[1];

                    if (!plugin.getLuckPermsHook().getAllSuffixes()
                            .contains(configSuffix + suffix.replace(configSuffix, ""))) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-remove-invalid", "%suffix%", suffix)));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return false;
                    }

                    try {
                        if (!plugin.getSuffixDatabase().getAllSuffixes().contains(suffix)) {
                            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-remove-not-in-list")));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            return false;
                        }

                        plugin.getSuffixDatabase().removeSuffix(suffix);
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-remove-success", "%suffix%", suffix)));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);

                    } catch (SQLException e) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("db-error")));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        throw new RuntimeException(e);
                    }
                    return true;
                }

                case "clear": {
                    try {
                        plugin.getSuffixDatabase().clearSuffixes();
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-clear-success")));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    } catch (SQLException e) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("db-error")));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        throw new RuntimeException(e);
                    }
                    return true;
                }

                case "info": {
                    if (plugin.getLuckPermsHook().getAllSuffixes().isEmpty()) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-info-no-suffixes")));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return false;
                    }

                    try {
                        if (plugin.getSuffixDatabase().getAllSuffixes().isEmpty()) {
                            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-info-footer")));
                            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-info-no-suffixes")));
                            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-info-footer")));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            return false;
                        }

                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffixes-header-nonpurchasable")));

                        for (String s : plugin.getSuffixDatabase().getAllSuffixes()) {
                            String color = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                    plugin.getLuckPermsHook().getGroupSuffixColor(configSuffix + s.replace(configSuffix, "")));
                            player.sendMessage(ChatUtil.parseLegacyColors("&7Suffix: " + color));
                        }

                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-info-footer")));

                    } catch (SQLException e) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("db-error")));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        throw new RuntimeException(e);
                    }
                    return true;
                }

                case "help": {
                    MiniMessage mm = MiniMessage.miniMessage();
                    String suffixArg = args.length >= 2 ? args[1] : "<suffix>";

                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-divider")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-creating-title")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-step1", "%suffix%", suffixArg))
                            .hoverEvent(HoverEvent.showText(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-click-here"))))
                            .clickEvent(ClickEvent.suggestCommand("/lp creategroup suffix_" + suffixArg)));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-step2"))
                            .hoverEvent(HoverEvent.showText(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-click-here"))))
                            .clickEvent(ClickEvent.suggestCommand("/lp editor")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-step3")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-divider")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-nonpurchasable-title")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-nonpurchasable-step1", "%suffix%", suffixArg))
                            .hoverEvent(HoverEvent.showText(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-click-here"))))
                            .clickEvent(ClickEvent.suggestCommand("/suffix add " + suffixArg)));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-divider")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-checking-title")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-checking-step1"))
                            .hoverEvent(HoverEvent.showText(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-click-here"))))
                            .clickEvent(ClickEvent.suggestCommand("/suffixes")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-divider")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-permission-title")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-permission-step1"))
                            .hoverEvent(HoverEvent.showText(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-click-here"))))
                            .clickEvent(ClickEvent.suggestCommand("/lp user <player> permission setsuffixsplus.suffix." + suffixArg)));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-permission-step2", "%suffix%", suffixArg))
                            .hoverEvent(HoverEvent.showText(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-help-click-here"))))
                            .clickEvent(ClickEvent.suggestCommand("/lp user <player> permission setsuffixsplus.suffix." + suffixArg)));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-support-divider")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-support-label"))
                            .clickEvent(ClickEvent.openUrl("https://discord.gg/SuypvRBa4c"))
                            .hoverEvent(HoverEvent.showText(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-support-hover")))));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-support-divider")));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    return true;
                }

                case "support": {
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-support-divider")));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-support-label"))
                            .clickEvent(ClickEvent.openUrl("https://discord.gg/SuypvRBa4c"))
                            .hoverEvent(HoverEvent.showText(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-support-hover")))));
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-support-divider")));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    return true;
                }

                default: {
                    player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-unknown-subcommand")));
                    return false;
                }
            }
        }

        // Open GUI
        try {
            plugin.guiManager.openSuffixGui(player, 0);
        } catch (SQLException e) {
            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("db-error")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player player)) return null;

        if (!player.hasPermission("suffixsplus.command.suffixadmin")) {
            if (args.length == 1 && plugin.getConfig().getBoolean("suffix.notification", true))
                completions.add("notification");
            String lastArg = args[args.length - 1];
            return completions.stream().filter(s -> s.startsWith(lastArg)).collect(Collectors.toList());
        }

        if (args.length == 1) {
            completions.add("add");
            completions.add("remove");
            if (plugin.getConfig().getBoolean("suffix.notification", true)) completions.add("notification");
            completions.add("clear");
            completions.add("help");
            completions.add("info");
            completions.add("support");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("add")) {
                for (String suffix : plugin.getLuckPermsHook().getAllSuffixes()) {
                    try {
                        if (!plugin.getSuffixDatabase().getAllSuffixes().contains(suffix)) {
                            completions.add(suffix.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), ""));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else if (args[0].equalsIgnoreCase("remove")) {
                try {
                    completions.addAll(plugin.getSuffixDatabase().getAllSuffixes());
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } else if (args[0].equalsIgnoreCase("help")) {
                for (String suffix : plugin.getLuckPermsHook().getAllSuffixes()) {
                    completions.add(suffix.replace("suffix_", ""));
                }
            }
        }

        String lastArg = args[args.length - 1];
        return completions.stream().filter(s -> s.startsWith(lastArg)).collect(Collectors.toList());
    }
}