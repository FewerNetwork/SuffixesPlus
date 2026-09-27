package me.zombieman.dev.suffixesplus.commands;

import me.zombieman.dev.suffixesplus.SuffixesPlus;
import me.zombieman.dev.suffixesplus.databases.CollectionData;
import me.zombieman.dev.suffixesplus.databases.CollectionDatabase;
import me.zombieman.dev.suffixesplus.hooks.LuckPermsHook;
import me.zombieman.dev.suffixesplus.utils.ChatUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CollectionCmd implements CommandExecutor, TabCompleter {

    private final SuffixesPlus plugin;

    private final LuckPermsHook luckPermsHook;

    public CollectionCmd(SuffixesPlus plugin, LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.luckPermsHook = luckPermsHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("suffixsplus.command.suffixadmin")) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("no-permission")));
            return false;
        }


        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        try {

            switch (args[0].toLowerCase()) {

                case "create" -> create(sender, args);

                case "remove" -> remove(sender, args);

                case "addincollection" -> addInCollection(sender, args);

                case "removefromcollection" -> removeFromCollection(sender, args);

                case "setcollectionlore" -> setCollectionLore(sender, args);

                case "seticon" -> setIcon(sender, args);

                default -> sendUsage(sender);
            }

        } catch (SQLException e) {
            sender.sendMessage("§cAn error occurred while accessing the database.");
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player player)) return null;
        if (!player.hasPermission("suffixsplus.command.collection")) return null;

        if (args.length == 1) {
            completions.add("create");
            completions.add("remove");
            completions.add("addincollection");
            completions.add("removefromcollection");
            completions.add("setcollectionlore");
            completions.add("seticon");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                completions.add("<name>");
            } else if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("addincollection") || args[0].equalsIgnoreCase("removefromcollection") || args[0].equalsIgnoreCase("setcollectionlore") || args[0].equalsIgnoreCase("seticon")) {
                List<String> collections = new ArrayList<>();
                collections.add("<collection>");

                try {
                    List<String> allCollections = plugin.getCollectionDatabase().getAllCollections();
                    if (!allCollections.isEmpty()) {
                        collections.removeFirst();
                        collections.addAll(allCollections);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                completions.addAll(collections);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("create")) {
                completions.add("<tag>");
            } else if (args[0].equalsIgnoreCase("setcollectionlore")) {
                completions.add("<line1, line2, line3>");
            } else if (args[0].equalsIgnoreCase("addincollection")) {
                List<String> suffixes = new ArrayList<>();
                suffixes.add("<suffix>");

                try {
                    List<String> allSuffixes = luckPermsHook.getAllSuffixes();
                    if (!allSuffixes.isEmpty()) {
                        CollectionData collection = plugin.getCollectionDatabase().getCollection(args[1]);
                        boolean use = false;
                        if (collection != null) use = true;

                        List<String> collectionSuffixes =  new ArrayList<>();

                        if (use) collectionSuffixes = plugin.getCollectionDatabase().fromStringToList(collection.getSuffix());

                        suffixes.removeFirst();

                        for (String suffix : allSuffixes) {
                            String formattedSuffix = suffix.replace("suffix_", "");
                            if (collectionSuffixes.contains(formattedSuffix)) continue;
                            suffixes.add(formattedSuffix);
                        }

                        if (suffixes.isEmpty()) collection.setSuffix("<suffix>");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                completions.addAll(suffixes);
            } else if (args[0].equalsIgnoreCase("removefromcollection")) {
                List<String> suffixes = new ArrayList<>();
                suffixes.add("<suffix>");

                try {
                    CollectionData collection = plugin.getCollectionDatabase().getCollection(args[1]);
                    if (collection != null) {
                        suffixes.removeFirst();
                        suffixes.addAll(plugin.getCollectionDatabase().fromStringToList(collection.getSuffix()));
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                completions.addAll(suffixes);
            } else if (args[0].equalsIgnoreCase("seticon")) {
                for (Material material : Material.values()) {
                    completions.add(material.name());
                }
            }
        } else if (args.length > 2) {
            if (args[0].equalsIgnoreCase("setcollectionlore")) completions.add("<line1, line2, line3>");
        }

        String lastArg = args[args.length - 1];
        return completions.stream().filter(s -> s.startsWith(lastArg)).collect(Collectors.toList());
    }

    private void create(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }

        String collection = args[1];

        CollectionDatabase collectionDatabase = plugin.getCollectionDatabase();
        if (collectionDatabase.collectionExists(collection)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-exists", "%collection%", collection)));
            return;
        }

        String tag = args[2];

        collectionDatabase.createCollection(collection, tag);
        sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-created", "%collection%", collection, "%tag%", tag)));
    }

    private void remove(CommandSender sender, String[] args) throws SQLException {

        String collection = args[1];

        CollectionDatabase collectionDatabase = plugin.getCollectionDatabase();
        if (!collectionDatabase.collectionExists(collection)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-does-not-exist", "%collection%", collection)));
            return;
        }

        collectionDatabase.removeCollection(collection);
        sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-removed", "%collection%", collection)));
    }

    private void addInCollection(CommandSender sender, String[] args) throws SQLException {
        String collection = args[1];
        CollectionDatabase collectionDatabase = plugin.getCollectionDatabase();

        if (!collectionDatabase.collectionExists(collection)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-does-not-exist", "%collection%", collection)));
            return;
        }

        String suffix = args[2].replace("suffix_", "");

        if (!luckPermsHook.getAllSuffixes().contains("suffix_" + suffix)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-remove-invalid", "%suffix%", suffix)));
            return;
        }

        if (!collectionDatabase.addSuffix(collection, suffix)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-suffix-already-in-collection", "%collection%", collection, "%suffix%", suffix)));
            return;
        }
        sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-suffix-added", "%collection%", collection, "%suffix%", suffix)));
    }

    private void removeFromCollection(CommandSender sender, String[] args) throws SQLException {
        String collection = args[1];
        CollectionDatabase collectionDatabase = plugin.getCollectionDatabase();

        if (!collectionDatabase.collectionExists(collection)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-does-not-exist", "%collection%", collection)));
            return;
        }

        String suffix = args[2];

        if (!luckPermsHook.getAllSuffixes().contains("suffix_" + suffix.replace("suffix_", ""))) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("suffix-remove-invalid", "%suffix%", suffix)));
            return;
        }

        if (!collectionDatabase.removeSuffix(collection, suffix)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-suffix-not-in-collection", "%collection%", collection, "%suffix%", suffix)));
            return;
        }

        sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-suffix-removed", "%collection%", collection, "%suffix%", suffix)));
    }

    private void setCollectionLore(CommandSender sender, String[] args) throws SQLException {
        String collection = args[1];
        CollectionDatabase collectionDatabase = plugin.getCollectionDatabase();

        if (!collectionDatabase.collectionExists(collection)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-does-not-exist", "%collection%", collection)));
            return;
        }
        String result = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        List<String> lore = collectionDatabase.fromStringToList(result);
        collectionDatabase.setLore(collection, collectionDatabase.fromListToString(lore));

        sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-set-lore", "%collection%", collection, "%lore%", result)));
    }

    private void setIcon(CommandSender sender, String[] args) throws SQLException {
        String collection = args[1];
        CollectionDatabase collectionDatabase = plugin.getCollectionDatabase();

        if (!collectionDatabase.collectionExists(collection)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-does-not-exist", "%collection%", collection)));
            return;
        }

        String icon = args[2];

        if (collectionDatabase.setIcon(collection, icon)) {
            sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-set-icon-sucess", "%collection%", collection, "%icon%", icon)));
            return;
        }

        sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-set-icon-failed", "%collection%", collection, "%icon%", icon)));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-usage")));
    }
}