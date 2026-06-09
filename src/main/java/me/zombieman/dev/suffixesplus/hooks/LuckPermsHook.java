package me.zombieman.dev.suffixesplus.hooks;

import me.zombieman.dev.suffixesplus.SuffixesPlus;
import me.zombieman.dev.suffixesplus.utils.ChatUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class LuckPermsHook {
    private final LuckPerms luckPerms;
    private final SuffixesPlus plugin;

    public LuckPermsHook(SuffixesPlus plugin, LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
        this.plugin = plugin;
    }

    public List<String> getAllSuffixes() {
        return luckPerms.getGroupManager().getLoadedGroups().stream()
                .filter(group -> group.getName().startsWith(plugin.getConfig().getString("suffix.prefix", "suffix_")))
                .map(Group::getName)
                .collect(Collectors.toList());
    }

    public List<String> getPlayerSuffixes(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return List.of();

        List<String> allSuffixes = getAllSuffixes();
        List<String> playerSuffixes = new ArrayList<>();

        String configPrefix = plugin.getConfig().getString("suffix.prefix", "suffix_");
        String playerName = player.getName();

        for (String fullSuffix : allSuffixes) {
            String suffix = fullSuffix.replace(configPrefix, "");
            String permission = "suffixsplus.suffix." + suffix;
            boolean hasPermission = player.hasPermission(permission);

            boolean hasDatabaseAccess = false;
            try {
                hasDatabaseAccess = checkAccessAsync(playerUUID, playerName, configPrefix, fullSuffix).get();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (hasPermission || hasDatabaseAccess) {
                playerSuffixes.add(fullSuffix);
            }
        }

        return playerSuffixes;
    }

    public void checkAccess(UUID playerUUID, String playerName, String configSuffix, String suffix, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean result = false;
            try {
                String purchased = plugin.getDatabase().getPlayer(playerUUID, playerName).getPurchased();
                List<String> allSuffixes = plugin.getSuffixDatabase().getAllSuffixes();
                String replace = suffix.replace(configSuffix, "");

                if (purchased != null && !allSuffixes.contains(replace) && purchased.equalsIgnoreCase("purchasable"))
                    result = true;
                if (purchased != null && allSuffixes.contains(replace) && purchased.equalsIgnoreCase("all"))
                    result = true;
            } catch (SQLException e) {
                e.printStackTrace();
            }

            boolean finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        });
    }

    public CompletableFuture<Boolean> checkAccessAsync(UUID uuid, String name, String configSuffix, String suffix) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String purchased = plugin.getDatabase().getPlayer(uuid, name).getPurchased();
                List<String> allSuffixes = plugin.getSuffixDatabase().getAllSuffixes();
                String replace = suffix.replace(configSuffix, "");

                if (purchased != null && !allSuffixes.contains(replace) && purchased.equalsIgnoreCase("purchasable"))
                    return true;
                if (purchased != null && purchased.equalsIgnoreCase("all"))
                    return true;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        });
    }

    public Optional<String> getFormattedSuffix(UUID playerUUID) {
        User user = luckPerms.getUserManager().getUser(playerUUID);
        if (user == null) return Optional.empty();

        return user.getNodes(NodeType.SUFFIX).stream()
                .map(SuffixNode.class::cast)
                .max((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .map(SuffixNode::getMetaValue);
    }

    public List<String> getAllFormattedSuffixes(UUID playerUUID) {
        User user = luckPerms.getUserManager().getUser(playerUUID);
        if (user == null) return List.of();

        return user.getNodes(NodeType.SUFFIX).stream()
                .map(SuffixNode.class::cast)
                .map(SuffixNode::getMetaValue)
                .collect(Collectors.toList());
    }

    public String getGroupSuffixColor(String groupName) {
        groupName = groupName.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");
        groupName = plugin.getConfig().getString("suffix.prefix", "suffix_") + groupName;

        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) return null;

        Optional<SuffixNode> suffixNode = group.getNodes(NodeType.SUFFIX)
                .stream()
                .max(Comparator.comparingInt(SuffixNode::getPriority));

        return suffixNode.map(SuffixNode::getMetaValue).orElse(null);
    }

    public boolean clearAllSuffixes(UUID uuid, boolean message) {
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return false;

        boolean suffixRemoved = false;
        boolean groupRemoved = false;

        String suffixPrefix = plugin.getConfig().getString("suffix.prefix", "suffix_");

        List<InheritanceNode> groupNodes = user.getNodes(NodeType.INHERITANCE).stream()
                .map(InheritanceNode.class::cast)
                .filter(n -> n.getGroupName().startsWith(suffixPrefix))
                .collect(Collectors.toList());

        for (InheritanceNode groupNode : groupNodes) {
            user.data().remove(groupNode);
            groupRemoved = true;
        }

        List<SuffixNode> suffixNodes = user.getNodes(NodeType.SUFFIX).stream()
                .map(SuffixNode.class::cast)
                .collect(Collectors.toList());

        for (SuffixNode suffixNode : suffixNodes) {
            if (suffixNode.getMetaValue().startsWith(suffixPrefix)) {
                user.data().remove(suffixNode);
                suffixRemoved = true;
                plugin.getLogger().info("Removed suffix '" + suffixNode.getMetaValue() + "' from player: " + uuid);
            }
        }

        if (message) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (!suffixRemoved && !groupRemoved) {
                    player.sendMessage(ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("lp-clear-no-suffixes-divider")));
                    player.sendMessage(ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("lp-clear-no-suffixes")));
                    player.sendMessage(ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("lp-clear-no-suffixes-divider")));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } else {
                    player.sendMessage(ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("lp-clear-success-divider")));
                    player.sendMessage(ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("lp-clear-success")));
                    player.sendMessage(ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("lp-clear-success-divider")));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                }
            }
        }

        luckPerms.getUserManager().saveUser(user);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabase().updateSuffixes(uuid, "n/a");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
        return true;
    }

    public void removeSuffix(Player player, String suffix) {
        suffix = suffix.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");
        suffix = plugin.getConfig().getString("suffix.prefix", "suffix_") + suffix;

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            plugin.getLogger().warning("User not found for UUID: " + player.getUniqueId());
            return;
        }

        boolean suffixRemoved = false;
        boolean groupRemoved = false;

        String finalSuffix = suffix;
        Optional<SuffixNode> suffixNodeOptional = user.getNodes(NodeType.SUFFIX).stream()
                .map(SuffixNode.class::cast)
                .filter(n -> n.getMetaValue().equalsIgnoreCase(finalSuffix))
                .findFirst();

        if (suffixNodeOptional.isPresent()) {
            user.data().remove(suffixNodeOptional.get());
            suffixRemoved = true;
            plugin.getLogger().info("Removed suffix '" + suffix + "' from player: " + player.getName());
        }

        String suffixPrefix = plugin.getConfig().getString("suffix.prefix", "suffix_");
        String groupName = suffixPrefix + suffix.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");

        Optional<InheritanceNode> groupNodeOptional = user.getNodes(NodeType.INHERITANCE).stream()
                .map(InheritanceNode.class::cast)
                .filter(n -> n.getGroupName().equalsIgnoreCase(groupName))
                .findFirst();

        if (groupNodeOptional.isPresent()) {
            user.data().remove(groupNodeOptional.get());
            groupRemoved = true;
            plugin.getLogger().info("Removed group '" + groupName + "' from player: " + player.getName());
        }

        if (!suffixRemoved && !groupRemoved) {
            player.sendMessage(ChatUtil.parseLegacyColors(
                    plugin.getMessageManager().getRaw("lp-remove-nothing-removed")));
        }

        luckPerms.getUserManager().saveUser(user);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabase().updateSuffixes(player.getUniqueId(), "n/a");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public boolean hasSuffix(UUID playerUUID, String suffix) {
        suffix = suffix.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");
        suffix = plugin.getConfig().getString("suffix.prefix", "suffix_") + suffix;

        User user = luckPerms.getUserManager().getUser(playerUUID);
        if (user == null) return false;

        String suffixPrefix = plugin.getConfig().getString("suffix.prefix", "suffix_");
        String expectedGroupName = suffixPrefix + suffix.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");

        return user.getNodes(NodeType.INHERITANCE).stream()
                .map(InheritanceNode.class::cast)
                .anyMatch(n -> n.getGroupName().equalsIgnoreCase(expectedGroupName));
    }

    public void addGroup(UUID playerUUID, String groupName) {
        groupName = groupName.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");
        groupName = plugin.getConfig().getString("suffix.prefix", "suffix_") + groupName;

        User user = luckPerms.getUserManager().getUser(playerUUID);
        if (user == null) {
            plugin.getLogger().warning("User not found for UUID: " + playerUUID);
            return;
        }

        InheritanceNode groupNode = InheritanceNode.builder(groupName).build();
        user.data().add(groupNode);
        luckPerms.getUserManager().saveUser(user);
        Bukkit.getLogger().info("Added group '" + groupName + "' to player: " + user.getUniqueId());
    }

    public boolean isPurchasable(String groupName) throws SQLException {
        return plugin.getSuffixDatabase().suffixDoesNotExists(groupName);
    }

    public boolean groupHasPermission(String groupName, String permission) {
        String prefix = plugin.getConfig().getString("suffix.prefix", "suffix_");
        if (!groupName.startsWith(prefix)) {
            groupName = prefix + groupName;
        }

        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            plugin.getLogger().warning("Group not found: " + groupName);
            return false;
        }

        return group.getNodes().stream()
                .filter(node -> node.getKey().equalsIgnoreCase(permission))
                .anyMatch(node -> node.getValue());
    }
}