package me.zombieman.dev.suffixesplus.managers;

import me.clip.placeholderapi.PlaceholderAPI;
import me.zombieman.dev.suffixesplus.SuffixesPlus;
import me.zombieman.dev.suffixesplus.databases.CollectionData;
import me.zombieman.dev.suffixesplus.hooks.LuckPermsHook;
import me.zombieman.dev.suffixesplus.utils.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GuiManager {

    private final SuffixesPlus plugin;
    private final LuckPermsHook luckPermsHook;

    public GuiManager(SuffixesPlus plugin, LuckPermsHook luckPermsHook) {
        this.plugin = plugin;
        this.luckPermsHook = luckPermsHook;
    }

    private final Map<UUID, Integer> playerPages = new HashMap<>();

    private final Map<UUID, Boolean> collectionsViewEnabled = new HashMap<>();

    public boolean isCollectionsViewEnabled(Player player) {
        return collectionsViewEnabled.getOrDefault(player.getUniqueId(), false);
    }

    public void setCollectionsViewEnabled(Player player, boolean enabled) {
        collectionsViewEnabled.put(player.getUniqueId(), enabled);
    }

    public void openSuffixGui(Player player, int page) throws SQLException {
        if (isCollectionsViewEnabled(player)) {
            openCollectionsOverviewGui(player, page);
        } else {
            openFlatSuffixGui(player, page);
        }
    }

    private void openFlatSuffixGui(Player player, int page) throws SQLException {

        List<String> allSuffixes = luckPermsHook.getAllSuffixes();
        allSuffixes.removeIf(suffix -> suffix == null || suffix.isEmpty());

        if (allSuffixes.isEmpty()) {
            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("gui-no-suffixes")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        boolean hasCollections = !plugin.getCollectionDatabase().getAllCollectionData().isEmpty();

        int suffixesPerPage = 36;
        int maxPage = (int) Math.ceil((double) allSuffixes.size() / suffixesPerPage);

        String title = plugin.getMessageManager().getRaw("gui-title",
                "%page%", String.valueOf(page + 1),
                "%maxPage%", String.valueOf(maxPage));
        Inventory gui = Bukkit.createInventory(null, 5 * 9, MiniMessage.miniMessage().deserialize(title));

        // Fill row 5 with black panes
        ItemStack blackPane = createPane();
        for (int i = 36; i <= 44; i++) {
            gui.setItem(i, blackPane);
        }

        List<CompletableFuture<ItemStack>> futures = new ArrayList<>();
        for (String suffix : allSuffixes) {
            CompletableFuture<ItemStack> suffixItemAsync = createSuffixItemAsync(player, suffix);
            if (suffixItemAsync == null) continue;

            futures.add(suffixItemAsync.thenApply(item -> {
                if (item == null) System.out.println("[SuffixGUI] Suffix is null, skipping: " + suffix);
                return item;
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {

            List<ItemStack> owned = new ArrayList<>();
            List<ItemStack> notOwned = new ArrayList<>();

            for (CompletableFuture<ItemStack> future : futures) {
                try {
                    if (future.get() == null) continue;
                    ItemStack item = future.get();
                    ItemMeta meta = item.getItemMeta();
                    boolean owns = meta != null && Boolean.TRUE.equals(
                            meta.getPersistentDataContainer().get(
                                    new NamespacedKey(plugin, "ownsSuffix"), PersistentDataType.BOOLEAN));
                    if (owns) owned.add(item);
                    else notOwned.add(item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            List<ItemStack> allItems = new ArrayList<>();
            allItems.addAll(owned);
            allItems.addAll(notOwned);

            int startIndex = page * suffixesPerPage;
            int endIndex = Math.min(startIndex + suffixesPerPage, allItems.size());

            for (int i = startIndex, slot = 0; i < endIndex && slot < 36; i++, slot++) {
                gui.setItem(slot, allItems.get(i));
            }

            createPlayerHead(player, luckPermsHook.getPlayerSuffixes(player.getUniqueId()).size())
                    .thenAccept(head -> gui.setItem(43, head));

            gui.setItem(40, createClearAllButton());
            gui.setItem(41, createRandomSuffixButton());
            gui.setItem(39, createInfoButton());
            if (hasCollections) {
                gui.setItem(38, createCollectionsToggleButton(false));
            }

            if (page < maxPage - 1)
                gui.setItem(44, createNavigationButton(plugin.getMessageManager().getRaw("gui-nav-next"), "next", Material.ARROW, null));
            if (page > 0)
                gui.setItem(36, createNavigationButton(plugin.getMessageManager().getRaw("gui-nav-previous"), "previous", Material.ARROW, null));

            try {
                if (player.hasPermission("suffixsplus.gui.preview")) {
                    ItemStack preview = createPreview(player);
                    gui.setItem(page > 0 ? 37 : 36, preview);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                setCurrentPage(player, page);
                player.openInventory(gui);
            });

        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    private void openCollectionsOverviewGui(Player player, int page) throws SQLException {

        List<String> allSuffixes = luckPermsHook.getAllSuffixes();
        allSuffixes.removeIf(suffix -> suffix == null || suffix.isEmpty());

        if (allSuffixes.isEmpty()) {
            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("gui-no-suffixes")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String configSuffix = plugin.getConfig().getString("suffix.prefix", "suffix_");
        List<CollectionData> collections = plugin.getCollectionDatabase().getAllCollectionData();

        // lower-cased member suffix name -> the collection it belongs to
        // (first collection that claims a given suffix wins).
        Map<String, CollectionData> memberToCollection = new HashMap<>();
        Map<String, List<String>> collectionMembers = new LinkedHashMap<>();
        for (CollectionData collection : collections) {
            List<String> members = plugin.getCollectionDatabase().fromStringToList(collection.getSuffix());
            collectionMembers.put(collection.getId(), new ArrayList<>());
            for (String member : members) {
                if (member == null || member.isBlank()) continue;
                memberToCollection.putIfAbsent(member.toLowerCase(), collection);
            }
        }

        List<String> ungroupedSuffixes = new ArrayList<>();
        for (String suffix : allSuffixes) {
            String formatted = suffix.replace(configSuffix, "");
            CollectionData collection = memberToCollection.get(formatted.toLowerCase());
            if (collection != null) {
                collectionMembers.get(collection.getId()).add(suffix);
            } else {
                ungroupedSuffixes.add(suffix);
            }
        }

        int suffixesPerPage = 36;

        List<CompletableFuture<ItemStack>> collectionFutures = new ArrayList<>();
        for (CollectionData collection : collections) {
            List<String> members = collectionMembers.get(collection.getId());
            if (members == null || members.isEmpty()) continue; // nothing to show for this collection right now
            collectionFutures.add(createCollectionItemAsync(player, collection, members));
        }

        List<CompletableFuture<ItemStack>> suffixFutures = new ArrayList<>();
        for (String suffix : ungroupedSuffixes) {
            CompletableFuture<ItemStack> future = createSuffixItemAsync(player, suffix);
            if (future != null) suffixFutures.add(future);
        }

        List<CompletableFuture<ItemStack>> allFutures = new ArrayList<>();
        allFutures.addAll(collectionFutures);
        allFutures.addAll(suffixFutures);

        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).thenRun(() -> {

            List<ItemStack> owned = new ArrayList<>();
            List<ItemStack> notOwned = new ArrayList<>();

            for (CompletableFuture<ItemStack> future : allFutures) {
                try {
                    ItemStack item = future.get();
                    if (item == null) continue;
                    ItemMeta meta = item.getItemMeta();
                    boolean owns = meta != null && Boolean.TRUE.equals(
                            meta.getPersistentDataContainer().get(
                                    new NamespacedKey(plugin, "ownsSuffix"), PersistentDataType.BOOLEAN));
                    if (owns) owned.add(item);
                    else notOwned.add(item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            List<ItemStack> allItems = new ArrayList<>();
            allItems.addAll(owned);
            allItems.addAll(notOwned);

            int maxPage = Math.max(1, (int) Math.ceil((double) allItems.size() / suffixesPerPage));

            String title = plugin.getMessageManager().getRaw("gui-title",
                    "%page%", String.valueOf(page + 1),
                    "%maxPage%", String.valueOf(maxPage));
            Inventory gui = Bukkit.createInventory(null, 5 * 9, MiniMessage.miniMessage().deserialize(title));

            ItemStack blackPane = createPane();
            for (int i = 36; i <= 44; i++) {
                gui.setItem(i, blackPane);
            }

            int startIndex = page * suffixesPerPage;
            int endIndex = Math.min(startIndex + suffixesPerPage, allItems.size());
            for (int i = startIndex, slot = 0; i < endIndex && slot < 36; i++, slot++) {
                gui.setItem(slot, allItems.get(i));
            }

            createPlayerHead(player, luckPermsHook.getPlayerSuffixes(player.getUniqueId()).size())
                    .thenAccept(head -> gui.setItem(43, head));

            gui.setItem(40, createClearAllButton());
            gui.setItem(41, createRandomSuffixButton());
            gui.setItem(39, createInfoButton());
            gui.setItem(38, createCollectionsToggleButton(true));

            if (page < maxPage - 1)
                gui.setItem(44, createNavigationButton(plugin.getMessageManager().getRaw("gui-nav-next"), "next", Material.ARROW, null));
            if (page > 0)
                gui.setItem(36, createNavigationButton(plugin.getMessageManager().getRaw("gui-nav-previous"), "previous", Material.ARROW, null));

            try {
                if (player.hasPermission("suffixsplus.gui.preview")) {
                    ItemStack preview = createPreview(player);
                    gui.setItem(page > 0 ? 37 : 36, preview);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                setCurrentPage(player, page);
                player.openInventory(gui);
            });

        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    public void openCollectionGui(Player player, String collectionId, int page) throws SQLException {
        CollectionData collection = plugin.getCollectionDatabase().getCollection(collectionId);
        if (collection == null) {
            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("collection-not-found", "%collection%", collectionId)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String configSuffix = plugin.getConfig().getString("suffix.prefix", "suffix_");
        List<String> members = plugin.getCollectionDatabase().fromStringToList(collection.getSuffix());
        List<String> allSuffixes = luckPermsHook.getAllSuffixes();

        List<String> matchedSuffixes = new ArrayList<>();
        for (String suffix : allSuffixes) {
            if (suffix == null || suffix.isEmpty()) continue;
            String formatted = suffix.replace(configSuffix, "");
            for (String member : members) {
                if (member.equalsIgnoreCase(formatted)) {
                    matchedSuffixes.add(suffix);
                    break;
                }
            }
        }

        if (matchedSuffixes.isEmpty()) {
            player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("gui-collection-empty",
                    "%collection%", String.valueOf(collection.getCollection()))));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int suffixesPerPage = 36;
        List<CompletableFuture<ItemStack>> futures = new ArrayList<>();
        for (String suffix : matchedSuffixes) {
            CompletableFuture<ItemStack> future = createSuffixItemAsync(player, suffix);
            if (future != null) futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {

            List<ItemStack> owned = new ArrayList<>();
            List<ItemStack> notOwned = new ArrayList<>();

            for (CompletableFuture<ItemStack> future : futures) {
                try {
                    ItemStack item = future.get();
                    if (item == null) continue;
                    ItemMeta meta = item.getItemMeta();
                    boolean owns = meta != null && Boolean.TRUE.equals(
                            meta.getPersistentDataContainer().get(
                                    new NamespacedKey(plugin, "ownsSuffix"), PersistentDataType.BOOLEAN));
                    if (owns) owned.add(item);
                    else notOwned.add(item);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            List<ItemStack> allItems = new ArrayList<>();
            allItems.addAll(owned);
            allItems.addAll(notOwned);

            int maxPage = Math.max(1, (int) Math.ceil((double) allItems.size() / suffixesPerPage));

            String pageSuffix = plugin.getMessageManager().getRaw("gui-collection-title-page",
                    "%page%", String.valueOf(page + 1), "%maxPage%", String.valueOf(maxPage));
            String rawName = collection.getCollection() != null ? collection.getCollection() : collection.getId();
            Component title = ChatUtil.parseLegacyColors(rawName + " ")
                    .append(MiniMessage.miniMessage().deserialize(pageSuffix));

            Inventory gui = Bukkit.createInventory(null, 5 * 9, title);

            ItemStack blackPane = createPane();
            for (int i = 36; i <= 44; i++) {
                gui.setItem(i, blackPane);
            }

            int startIndex = page * suffixesPerPage;
            int endIndex = Math.min(startIndex + suffixesPerPage, allItems.size());
            for (int i = startIndex, slot = 0; i < endIndex && slot < 36; i++, slot++) {
                gui.setItem(slot, allItems.get(i));
            }

            gui.setItem(40, createBackButton());

            if (page < maxPage - 1)
                gui.setItem(44, createNavigationButton(plugin.getMessageManager().getRaw("gui-nav-next"), "next", Material.ARROW, collectionId));
            if (page > 0)
                gui.setItem(36, createNavigationButton(plugin.getMessageManager().getRaw("gui-nav-previous"), "previous", Material.ARROW, collectionId));

            Bukkit.getScheduler().runTask(plugin, () -> {
                setCurrentPage(player, page);
                player.openInventory(gui);
            });

        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }

    private CompletableFuture<ItemStack> createCollectionItemAsync(Player player, CollectionData collection, List<String> members) {
        List<CompletableFuture<ItemStack>> memberFutures = new ArrayList<>();
        for (String member : members) {
            CompletableFuture<ItemStack> future = createSuffixItemAsync(player, member);
            if (future != null) memberFutures.add(future);
        }

        return CompletableFuture.allOf(memberFutures.toArray(new CompletableFuture[0])).thenApply(v -> {
            boolean ownsAny = false;
            for (CompletableFuture<ItemStack> future : memberFutures) {
                try {
                    ItemStack item = future.get();
                    if (item == null) continue;
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && Boolean.TRUE.equals(meta.getPersistentDataContainer()
                            .get(new NamespacedKey(plugin, "ownsSuffix"), PersistentDataType.BOOLEAN))) {
                        ownsAny = true;
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return createCollectionItem(collection, members.size(), ownsAny);
        });
    }

    private ItemStack createCollectionItem(CollectionData collection, int memberCount, boolean ownsAny) {
        Material material = collection.getIcon() != null ? Material.matchMaterial(collection.getIcon()) : null;
        if (material == null) material = Material.CHEST;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String rawName = collection.getCollection() != null ? collection.getCollection() : collection.getId();
        meta.displayName(ChatUtil.parseLegacyColors(rawName));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        String rawLore = collection.getLore();
        if (rawLore != null && !rawLore.isBlank()) {
            List<String> loreLines = plugin.getCollectionDatabase().fromStringToList(rawLore);

            for (String line : loreLines) {
                lore.add(ChatUtil.parseLegacyColors(line.trim()));
            }
            lore.add(Component.empty());
        }

        lore.add(MiniMessage.miniMessage().deserialize(
                plugin.getMessageManager().getRaw("gui-collection-lore-count", "%count%", String.valueOf(memberCount))));
        lore.add(MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-collection-lore-open")));
        meta.lore(lore);

        if (ownsAny) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "collectionId"), PersistentDataType.STRING, collection.getId());
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "ownsSuffix"), PersistentDataType.BOOLEAN, ownsAny);
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "collectionItem");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCollectionsToggleButton(boolean currentlyEnabled) {
        ItemStack toggleItem = new ItemStack(currentlyEnabled ? Material.ENDER_CHEST : Material.CHEST);
        ItemMeta meta = toggleItem.getItemMeta();
        String key = currentlyEnabled ? "gui-collections-toggle-on" : "gui-collections-toggle-off";
        meta.displayName(MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw(key)));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "toggleCollectionsView");
        toggleItem.setItemMeta(meta);
        return toggleItem;
    }

    private ItemStack createBackButton() {
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta meta = backItem.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-collection-back")));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "backToCollections");
        backItem.setItemMeta(meta);
        return backItem;
    }

    private ItemStack createNavigationButton(String label, String dataValue, Material material, String collectionId) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(label));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, dataValue);
        if (collectionId != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "collectionId"), PersistentDataType.STRING, collectionId);
        }
        button.setItemMeta(meta);
        return button;
    }

    private ItemStack createPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "true");
        pane.setItemMeta(meta);
        return pane;
    }

    public CompletableFuture<ItemStack> createPlayerHead(Player player, int ownedSuffixes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return plugin.getDatabase()
                        .getPlayer(player.getUniqueId(), player.getName())
                        .getCurrentSuffix();
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        }).thenCompose(currentSuffixFromDB -> {

            CompletableFuture<ItemStack> future = new CompletableFuture<>();

            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) playerHead.getItemMeta();

                Component currentSuffix = ChatUtil.parseLegacyColors(
                        plugin.getMessageManager().getRaw("gui-preview-current-suffix-default"));

                String finalCurrentSuffix = currentSuffixFromDB;
                String prefix = plugin.getConfig().getString("suffix.prefix", "suffix_");
                finalCurrentSuffix = prefix + finalCurrentSuffix.replace(prefix, "");

                if (luckPermsHook.getGroupSuffixColor(finalCurrentSuffix) != null) {
                    String color = ChatColor.translateAlternateColorCodes('&',
                            luckPermsHook.getGroupSuffixColor(finalCurrentSuffix));
                    currentSuffix = ChatUtil.parseLegacyColors(
                            plugin.getMessageManager().getRaw("gui-preview-current-suffix", "%suffix%", color));
                }

                meta.setOwningPlayer(player);
                meta.displayName(ChatUtil.parseLegacyColors(
                        PlaceholderAPI.setPlaceholders(player,
                                        plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"))
                                .replace(plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"), "&7")
                                + player.getName()
                ));

                meta.lore(List.of(
                        Component.empty(),
                        currentSuffix,
                        MiniMessage.miniMessage().deserialize(
                                plugin.getMessageManager().getRaw("gui-head-owned-suffixes",
                                        "%count%", String.valueOf(ownedSuffixes))),
                        Component.empty()
                ));

                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "playerHead");
                playerHead.setItemMeta(meta);
                future.complete(playerHead);
            });

            return future;
        });
    }

    public CompletableFuture<ItemStack> createSuffixItemAsync(Player player, String suffix) {
        String configSuffix = plugin.getConfig().getString("suffix.prefix", "suffix_");
        String formattedSuffix = suffix.replace(configSuffix, "");

        return plugin.getLuckPermsHook()
                .checkAccessAsync(player.getUniqueId(), player.getName(), configSuffix, formattedSuffix)
                .thenApply(hasAccess -> {
                    String permissionNode = "suffixsplus.suffix." + formattedSuffix.toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
                    if (!hasAccess && player.hasPermission(permissionNode)) hasAccess = true;
                    if (player.hasPermission("suffixsplus.access.all")) hasAccess = true;

                    try {
                        if (!plugin.getSuffixDatabase().getAllSuffixes().contains(formattedSuffix)
                                && player.hasPermission("suffixsplus.access.purchasable"))
                            hasAccess = true;
                    } catch (SQLException e) {
                        player.sendMessage(ChatUtil.parseLegacyColors(plugin.getMessageManager().getRaw("db-error")));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        e.printStackTrace();
                    }

                    boolean result = false;
                    for (String list : plugin.getConfig().getStringList("suffix.hide")) {
                        if (!hasAccess && luckPermsHook.groupHasPermission(suffix, "suffixsplus.type." + list)) {
                            result = true;
                        }
                    }

                    if (result) return null;

                    ItemStack item = createSuffixItem(suffix, hasAccess, player);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.getPersistentDataContainer().set(
                                new NamespacedKey(plugin, "ownsSuffix"), PersistentDataType.BOOLEAN, hasAccess);
                        item.setItemMeta(meta);
                    }
                    return item;
                });
    }

    private ItemStack createPreview(Player player) throws SQLException {
        ItemStack anvil = new ItemStack(Material.ANVIL);
        ItemMeta meta = anvil.getItemMeta();

        Component currentSuffix = ChatUtil.parseLegacyColors(
                plugin.getMessageManager().getRaw("gui-preview-current-suffix-default"));
        String currentSuffixFromDB = plugin.getDatabase().getPlayer(player.getUniqueId(), player.getName()).getCurrentSuffix();
        currentSuffixFromDB = plugin.getConfig().getString("suffix.prefix", "suffix_")
                + currentSuffixFromDB.replace(plugin.getConfig().getString("suffix.prefix", "suffix_"), "");

        if (luckPermsHook.getGroupSuffixColor(currentSuffixFromDB) != null) {
            String color = ChatColor.translateAlternateColorCodes('&',
                    luckPermsHook.getGroupSuffixColor(currentSuffixFromDB));
            currentSuffix = ChatUtil.parseLegacyColors(
                    plugin.getMessageManager().getRaw("gui-preview-current-suffix", "%suffix%", color));
        }

        meta.displayName(ChatUtil.parseLegacyColors(
                PlaceholderAPI.setPlaceholders(player, plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"))
                        .replace(plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"), "&7")
                        + player.getName()));

        meta.lore(List.of(
                Component.empty(),
                MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-preview-click")),
                currentSuffix,
                Component.empty()
        ));

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "preview_anvil");
        anvil.setItemMeta(meta);
        return anvil;
    }

    private ItemStack createClearAllButton() {
        ItemStack clearItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = clearItem.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-clear-all")));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "clearAll");
        clearItem.setItemMeta(meta);
        return clearItem;
    }

    private ItemStack createRandomSuffixButton() {
        ItemStack randomItem = new ItemStack(Material.DIAMOND);
        ItemMeta meta = randomItem.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-random-suffix")));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "randomSuffix");
        randomItem.setItemMeta(meta);
        return randomItem;
    }

    private ItemStack createInfoButton() {
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta meta = infoItem.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-info-button")));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "infoButton");
        infoItem.setItemMeta(meta);
        return infoItem;
    }

    private ItemStack createSuffixItem(String suffix, boolean ownsSuffix, Player player) {
        String configSuffix = plugin.getConfig().getString("suffix.prefix", "suffix_");
        String formattedSuffix = suffix.replace(configSuffix, "");
        if (!formattedSuffix.isEmpty()) {
            formattedSuffix = Character.toUpperCase(formattedSuffix.charAt(0)) + formattedSuffix.substring(1);
        }

        ItemStack suffixItem = ownsSuffix ? new ItemStack(Material.NAME_TAG) : new ItemStack(Material.PAPER);
        ItemMeta meta = suffixItem.getItemMeta();

        String nameKey = ownsSuffix ? "gui-suffix-name-owned" : "gui-suffix-name-unowned";
        meta.displayName(MiniMessage.miniMessage().deserialize(
                plugin.getMessageManager().getRaw(nameKey, "%suffix%", formattedSuffix)));

        if (ownsSuffix) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        String rank = PlaceholderAPI.setPlaceholders(player, plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"));
        rank = rank.replace(plugin.getConfig().getString("player.rankPlaceholder", "%vault_prefix%"), "&7");

        String groupSuffixColor = luckPermsHook.getGroupSuffixColor(suffix);
        if (groupSuffixColor == null) groupSuffixColor = "&7";

        boolean isPurchasable = true;
        try {
            List<String> allSuffixes = plugin.getSuffixDatabase().getAllSuffixes();
            if (allSuffixes.contains(suffix.replace(configSuffix, "").toLowerCase())) isPurchasable = false;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String colorConverted = ChatColor.translateAlternateColorCodes('&', groupSuffixColor);

        meta.lore(List.of(
                Component.empty(),
                MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-suffix-lore-suffix")),
                ChatUtil.parseLegacyColors(colorConverted),
                Component.empty(),
                MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-suffix-lore-preview")),
                ChatUtil.parseLegacyColors(rank + player.getName() + colorConverted),
                Component.empty(),
                MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-suffix-lore-active",
                        "%value%", String.valueOf(luckPermsHook.hasSuffix(player.getUniqueId(), suffix)))),
                MiniMessage.miniMessage().deserialize(plugin.getMessageManager().getRaw("gui-suffix-lore-purchasable",
                        "%value%", String.valueOf(isPurchasable)))
        ));

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "suffix"), PersistentDataType.STRING, suffix);
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "guiItem"), PersistentDataType.STRING, "suffixItem");
        suffixItem.setItemMeta(meta);
        return suffixItem;
    }

    public int getCurrentPage(Player player) {
        return playerPages.getOrDefault(player.getUniqueId(), 0);
    }

    public void setCurrentPage(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);
    }
}