package me.zombieman.dev.suffixesplus.managers;

import me.zombieman.dev.suffixesplus.SuffixesPlus;
import org.bukkit.ChatColor;
import org.bukkit.Sound;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DataManager {

    private final SuffixesPlus plugin;
    public DataManager(SuffixesPlus plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<String> getSuffixAsync(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String suffix = null;
            try {
                suffix = plugin.getDatabase().getPlayer(uuid, name).getCurrentSuffix();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return suffix;
        });
    }
}
