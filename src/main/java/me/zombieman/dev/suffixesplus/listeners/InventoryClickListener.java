package me.zombieman.dev.suffixesplus.listeners;

import me.clip.placeholderapi.PlaceholderAPI;
import me.zombieman.dev.suffixesplus.SuffixesPlus;
import me.zombieman.dev.suffixesplus.hooks.LuckPermsHook;
import me.zombieman.dev.suffixesplus.utils.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.List;

public class InventoryClickListener implements Listener {

    private final SuffixesPlus plugin;
    private final LuckPermsHook luckPermsHook;

    public InventoryClickListener(SuffixesPlus plugin, LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.luckPermsHook = luckPermsHook;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta itemMeta = clickedItem.getItemMeta();
        NamespacedKey key = new NamespacedKey(plugin, "guiItem");

        if (!itemMeta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;

        String guiAction = itemMeta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

        event.setCancelled(true);

        int currentPage = plugin.guiManager.getCurrentPage(player);

        switch (guiAction) {
            case "playerHead":
                break;

            case "clearAll":
                player.closeInventory();
                clearAllSuffixes(player);
                break;

            case "randomSuffix":
                player.closeInventory();
                try {
                    assignRandomSuffix(player);
                } catch (SQLException e) {
                    player.sendMessage(plugin.getMessageManager().get("click-db-error"));
                    plugin.getLogger().warning("Database Warning: " + e.getMessage());
                }
                break;

            case "infoButton":
                player.closeInventory();
                player.sendMessage(plugin.getMessageManager().get("click-info"));
                break;

            case "suffixItem":
                player.closeInventory();
                handleSuffixSelection(player, itemMeta);
                break;

            case "next":
                try {
                    plugin.guiManager.openSuffixGui(player, currentPage + 1);
                } catch (SQLException e) {
                    player.sendMessage(plugin.getMessageManager().get("click-db-error"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                break;

            case "previous":
                try {
                    plugin.guiManager.openSuffixGui(player, currentPage - 1);
                } catch (SQLException e) {
                    player.sendMessage(plugin.getMessageManager().get("click-db-error"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                break;

            case "preview_anvil":
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        String currentSuffixFromDB = plugin.getDatabase()
                                .getPlayer(player.getUniqueId(), player.getName()).getCurrentSuffix();

                        if (currentSuffixFromDB == null || currentSuffixFromDB.equalsIgnoreCase("n/a")) {
                            player.sendMessage(plugin.getMessageManager().get("click-preview-no-suffix"));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            return;
                        }

                        player.sendMessage(plugin.getMessageManager().get("click-preview-header"));
                        player.sendMessage(Component.empty());

                        currentSuffixFromDB = plugin.getConfig().getString("suffix.prefix", "suffix_")
                                + currentSuffixFromDB.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");

                        String currentSuffix = ChatColor.translateAlternateColorCodes('&',
                                luckPermsHook.getGroupSuffixColor(currentSuffixFromDB));

                        player.sendMessage(ChatUtil.parseLegacyColors(
                                PlaceholderAPI.setPlaceholders(player, plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"))
                                        .replace(plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"), "&7")
                                        + player.getName() + currentSuffix));
                        player.sendMessage(Component.empty());
                        player.sendMessage(ChatUtil.parseLegacyColors(currentSuffix));
                        player.sendMessage(Component.empty());
                        player.closeInventory();

                    } catch (SQLException e) {
                        player.sendMessage(plugin.getMessageManager().get("click-db-error"));
                        e.printStackTrace();
                    }
                });
                break;

            default:
                break;
        }
    }

    private void clearAllSuffixes(Player player) {
        luckPermsHook.clearAllSuffixes(player.getUniqueId(), true);
    }

    private void assignRandomSuffix(Player player) throws SQLException {
        List<String> ownedSuffixes = luckPermsHook.getPlayerSuffixes(player.getUniqueId());

        if (ownedSuffixes.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().get("click-no-suffixes-random"));
            return;
        }

        String randomSuffix = ownedSuffixes.get((int) (Math.random() * ownedSuffixes.size()));
        randomSuffix = plugin.getConfig().getString("suffix.prefix", "suffix_")
                + randomSuffix.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");

        String finalRandomSuffix = randomSuffix;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String currentSuffix = plugin.getDatabase()
                        .getPlayer(player.getUniqueId(), player.getName()).getCurrentSuffix();

                if (finalRandomSuffix.equalsIgnoreCase(currentSuffix)) {
                    assignRandomSuffix(player);
                    return;
                }

                luckPermsHook.clearAllSuffixes(player.getUniqueId(), false);
                luckPermsHook.addGroup(player.getUniqueId(),
                        plugin.getConfig().getString("suffix.prefix", "suffix_")
                                + finalRandomSuffix.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), ""));

                plugin.getDatabase().updateSuffixes(player.getUniqueId(), finalRandomSuffix);

                String formattedSuffix = plugin.getConfig().getString("suffix.prefix", "suffix_")
                        + finalRandomSuffix.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String coloredSuffix = ChatColor.translateAlternateColorCodes('&',
                            luckPermsHook.getGroupSuffixColor(formattedSuffix));
                    player.sendMessage(ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("click-random-divider")));
                    player.sendMessage(ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("click-random-assigned", "%suffix%", coloredSuffix)));
                    player.sendMessage(ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("click-random-divider")));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                });

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void handleSuffixSelection(Player player, ItemMeta itemMeta) {
        NamespacedKey suffixKey = new NamespacedKey(plugin, "suffix");
        if (!itemMeta.getPersistentDataContainer().has(suffixKey, PersistentDataType.STRING)) return;

        String suffix = itemMeta.getPersistentDataContainer().get(suffixKey, PersistentDataType.STRING);
        if (suffix == null) {
            player.sendMessage(plugin.getMessageManager().get("click-no-valid-suffix"));
            return;
        }

        String cleanedSuffix = suffix.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");
        String permission = "suffixsplus.suffix." + cleanedSuffix;
        checkSuffixAccess(player, cleanedSuffix, permission);
    }

    private void checkSuffixAccess(Player player, String cleanedSuffix, String permission) {
        luckPermsHook.checkAccess(player.getUniqueId(), player.getName(),
                plugin.getConfig().getString("suffix.prefix", "suffix_"), cleanedSuffix, hasAccess -> {
                    if (hasAccess) {
                        handleSuffixAssignment(player, cleanedSuffix);
                    } else {
                        try {
                            if (player.hasPermission(permission) || player.hasPermission("suffixsplus.access.all")) {
                                handleSuffixAssignment(player, cleanedSuffix);
                            } else if (!plugin.getSuffixDatabase().getAllSuffixes().contains(cleanedSuffix)
                                    && player.hasPermission("suffixsplus.access.purchasable")) {
                                handleSuffixAssignment(player, cleanedSuffix);
                            } else {
                                handleSuffixPurchase(player, cleanedSuffix);
                            }
                        } catch (SQLException e) {
                            player.sendMessage(plugin.getMessageManager().get("db-error"));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            e.printStackTrace();
                        }
                    }
                });
    }

    private void handleSuffixAssignment(Player player, String cleanedSuffix) {
        if (luckPermsHook.hasSuffix(player.getUniqueId(), cleanedSuffix)) {
            luckPermsHook.removeSuffix(player, cleanedSuffix);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            String coloredSuffix = ChatColor.translateAlternateColorCodes('&',
                    luckPermsHook.getGroupSuffixColor(cleanedSuffix));
            player.sendMessage(ChatUtil.parseLegacyColors(
                    plugin.getMessageManager().getRaw("click-suffix-removed-divider")));
            player.sendMessage(ChatUtil.parseLegacyColors(
                    plugin.getMessageManager().getRaw("click-suffix-removed", "%suffix%", coloredSuffix)));
            player.sendMessage(ChatUtil.parseLegacyColors(
                    plugin.getMessageManager().getRaw("click-suffix-removed-divider")));
            return;
        }

        boolean complete = luckPermsHook.clearAllSuffixes(player.getUniqueId(), false);
        luckPermsHook.addGroup(player.getUniqueId(), cleanedSuffix);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        String rank = PlaceholderAPI.setPlaceholders(player,
                        plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"))
                .replace(plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"), "&7");
        String coloredSuffix = ChatColor.translateAlternateColorCodes('&',
                luckPermsHook.getGroupSuffixColor(cleanedSuffix));

        player.sendMessage(ChatUtil.parseLegacyColors(
                plugin.getMessageManager().getRaw("click-suffix-added-divider")));
        player.sendMessage(ChatUtil.parseLegacyColors(
                plugin.getMessageManager().getRaw("click-suffix-added")));
        player.sendMessage(ChatUtil.parseLegacyColors(rank + player.getName() + coloredSuffix));
        player.sendMessage(ChatUtil.parseLegacyColors(
                plugin.getMessageManager().getRaw("click-suffix-added-divider")));

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                if (complete) plugin.getDatabase().updateSuffixes(player.getUniqueId(), cleanedSuffix);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 20);
    }

    private void handleSuffixPurchase(Player player, String cleanedSuffix) {
        try {
            if (!luckPermsHook.isPurchasable(cleanedSuffix)) {
                player.sendMessage(plugin.getMessageManager().get("click-not-purchasable", "%suffix%", cleanedSuffix));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        } catch (SQLException e) {
            player.sendMessage(plugin.getMessageManager().get("click-purchase-check-error"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            e.printStackTrace();
            return;
        }

        for (String message : plugin.getConfig().getStringList("suffix.purchaseMessage")) {
            net.kyori.adventure.text.Component hoverText = MiniMessage.miniMessage().deserialize(
                    plugin.getConfig().getString("suffix.purchaseHoverMessage",
                                    "<aqua>Click here to purchase the %suffix% suffix!")
                            .replace("%suffix%", cleanedSuffix.replace(
                                    plugin.getConfig().getString("suffix.prefix", "suffix_"), "")));

            ClickEvent clickEvent = ClickEvent.openUrl(plugin.getConfig().getString("suffix.link", "https://"));

            player.sendMessage(MiniMessage.miniMessage().deserialize(
                            message.replace("%suffix%", cleanedSuffix.replace(
                                    plugin.getConfig().getString("suffix.prefix", "suffix_"), "")))
                    .hoverEvent(hoverText)
                    .clickEvent(clickEvent));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }
}