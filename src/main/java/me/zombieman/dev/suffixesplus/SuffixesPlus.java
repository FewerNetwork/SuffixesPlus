package me.zombieman.dev.suffixesplus;

import me.zombieman.dev.suffixesplus.commands.CollectionCmd;
import me.zombieman.dev.suffixesplus.commands.SuffixAdminCmd;
import me.zombieman.dev.suffixesplus.commands.SuffixCmd;
import me.zombieman.dev.suffixesplus.commands.SuffixesCmd;
import me.zombieman.dev.suffixesplus.databases.CollectionData;
import me.zombieman.dev.suffixesplus.databases.CollectionDatabase;
import me.zombieman.dev.suffixesplus.databases.PlayerDatabase;
import me.zombieman.dev.suffixesplus.databases.SuffixDatabase;
import me.zombieman.dev.suffixesplus.managers.DataManager;
import me.zombieman.dev.suffixesplus.managers.MessageManager;
import me.zombieman.dev.suffixesplus.managers.PurchasedManager;
import me.zombieman.dev.suffixesplus.managers.TempSuffixManager;
import me.zombieman.dev.suffixesplus.hooks.LuckPermsHook;
import me.zombieman.dev.suffixesplus.listeners.InventoryClickListener;
import me.zombieman.dev.suffixesplus.listeners.PlayerListener;
import me.zombieman.dev.suffixesplus.managers.GuiManager;
import me.zombieman.dev.suffixesplus.api.LuckPermsAPI;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;

public final class SuffixesPlus extends JavaPlugin {

    private LuckPermsHook luckPermsHook;
    private PlayerDatabase database;
    private CollectionDatabase collectionDatabase;
    private SuffixDatabase suffixDatabase;
    private TempSuffixManager tempSuffixManager;
    private PurchasedManager purchasedManager;
    private DataManager dataManager;
    private MessageManager messageManager;
    public GuiManager guiManager;
    public LuckPermsAPI luckPermsAPI;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // MessageManager must be created first so everything else can use it
        messageManager = new MessageManager(this);

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().warning("-----------------------------------------");
            getLogger().warning("WARNING");
            getLogger().warning(messageManager.getRaw("warn-luckperms-missing"));
            getLogger().warning(messageManager.getRaw("warn-plugin-disabling", "%plugin%", this.getPluginMeta().getName()));
            getLogger().warning("-----------------------------------------");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("-----------------------------------------");
            getLogger().warning("WARNING");
            getLogger().warning(messageManager.getRaw("warn-placeholderapi-missing"));
            getLogger().warning(messageManager.getRaw("warn-plugin-disabling", "%plugin%", this.getPluginMeta().getName()));
            getLogger().warning("-----------------------------------------");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        luckPermsAPI = new LuckPermsAPI(this);

        String username = this.getConfig().getString("database.username");
        String password = this.getConfig().getString("database.password");
        String url = this.getConfig().getString("database.url");

        try {
            database = new PlayerDatabase(url, username, password);
            collectionDatabase = new CollectionDatabase(url, username, password);
            suffixDatabase = new SuffixDatabase(url, username, password);
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe(messageManager.getRaw("warn-db-failed", "%error%", e.getMessage()));
            getLogger().severe("-----------------------------------------");
            getLogger().severe(messageManager.getRaw("warn-db-error"));
            getLogger().severe(messageManager.getRaw("warn-plugin-disabling", "%plugin%", this.getPluginMeta().getName()));
            getLogger().severe("-----------------------------------------");
            Bukkit.getPluginManager().disablePlugin(this);
        }

        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPermsHook = new LuckPermsHook(this, luckPerms);

        guiManager = new GuiManager(this, luckPermsHook);
        tempSuffixManager = new TempSuffixManager(this);
        purchasedManager = new PurchasedManager(this);
        dataManager = new DataManager(this);

        new PlayerListener(this);
        new InventoryClickListener(this, luckPermsHook);

        SuffixCmd suffixCmd = new SuffixCmd(this);
        SuffixesCmd suffixesCmd = new SuffixesCmd(this);
        SuffixAdminCmd suffixAdminCmd = new SuffixAdminCmd(this);
        CollectionCmd collectionCmd = new CollectionCmd(this, luckPermsHook);

        String suffixName = getConfig().getString("commands.suffix", "suffix");
        String suffixesName = getConfig().getString("commands.suffixes", "suffixes");
        String suffixAdminName = getConfig().getString("commands.suffixadmin", "suffixadmin");
        String collectionCmdName = getConfig().getString("commands.collection", "tag");

        registerCommand(suffixName, suffixCmd, suffixCmd);
        registerCommand(suffixesName, suffixesCmd, null);
        registerCommand(suffixAdminName, suffixAdminCmd, suffixAdminCmd);
        registerCommand(collectionCmdName, collectionCmd, collectionCmd);

        checkIfPlayerHasSuffix();
    }

    @Override
    public void onDisable() {
        if (getDatabase() != null) {
            getDatabase().close();
        }
        getSuffixDatabase().close();
    }

    public void checkIfPlayerHasSuffix() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                int onlinePlayers = Bukkit.getOnlinePlayers().size();
                if (onlinePlayers == 0) return;

                for (Player player : Bukkit.getOnlinePlayers()) {

                    String currentSuffix = getDatabase().getPlayer(player.getUniqueId(), player.getName()).getCurrentSuffix();
                    if (!currentSuffix.equalsIgnoreCase("n/a")) continue;

                    int ownedSuffixes = getLuckPermsHook().getPlayerSuffixes(player.getUniqueId()).size();
                    if (ownedSuffixes == 0) continue;

                    if (!getDatabase().getPlayer(player.getUniqueId(), player.getName()).getNotifications()
                            && getConfig().getBoolean("suffix.notification", true)) continue;

                    String countStr = String.valueOf(ownedSuffixes);

                    player.sendMessage(
                            messageManager.get("reminder-line-1")
                    );
                    player.sendMessage(
                            messageManager.get("reminder-title")
                    );
                    player.sendMessage(
                            messageManager.get("reminder-body", "%ownedSuffixes%", countStr)
                    );
                    player.sendMessage(
                            messageManager.get("reminder-command")
                                    .hoverEvent(HoverEvent.showText(messageManager.get("reminder-hover")))
                                    .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/suffix"))
                    );
                    player.sendMessage(
                            messageManager.get("reminder-line-end")
                    );

                    if (getConfig().getBoolean("suffix.notification", true)) {
                        player.sendMessage(
                                messageManager.get("reminder-notification-tip")
                                        .hoverEvent(HoverEvent.showText(messageManager.get("reminder-notification-tip-hover")))
                                        .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/suffix notification"))
                        );
                    }

                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                }
            } catch (SQLException e) {
                System.err.println("There was an error connecting to the database: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0L, 20L * 60 * 20);
    }


    public void reload() {
        reloadConfig();
        messageManager.load();
        guiManager = new GuiManager(this, luckPermsHook);
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            CommandMap commandMap = (CommandMap) field.get(Bukkit.getServer());

            Command command = new Command(name) {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    return executor.onCommand(sender, this, commandLabel, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    if (tabCompleter == null) return super.tabComplete(sender, alias, args);
                    List<String> result = tabCompleter.onTabComplete(sender, this, alias, args);
                    return result != null ? result : super.tabComplete(sender, alias, args);
                }
            };

            commandMap.register("suffixesplus", command);
        } catch (Exception e) {
            getLogger().severe("Failed to register command /" + name + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public MessageManager getMessageManager() { return messageManager; }
    public LuckPermsHook getLuckPermsHook() { return luckPermsHook; }
    public PlayerDatabase getDatabase() { return database; }
    public CollectionDatabase getCollectionDatabase() { return collectionDatabase; }
    public DataManager getDataManager() { return dataManager; }
    public SuffixDatabase getSuffixDatabase() { return suffixDatabase; }
    public PurchasedManager getPurchasedManager() { return purchasedManager; }
}