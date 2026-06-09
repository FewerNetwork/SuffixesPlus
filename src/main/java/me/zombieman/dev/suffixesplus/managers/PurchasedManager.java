package me.zombieman.dev.suffixesplus.managers;

import me.zombieman.dev.suffixesplus.SuffixesPlus;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

public class PurchasedManager {

    private final SuffixesPlus plugin;

    public PurchasedManager(SuffixesPlus plugin) {
        this.plugin = plugin;
    }

    public void givePurchasedRole(CommandSender sender, String playerName, String all) {

        if (all == null) {
            sender.sendMessage(plugin.getMessageManager().get("purchased-invalid-arguments"));
            return;
        }

        if (!all.equalsIgnoreCase("purchasable") && !all.equalsIgnoreCase("all") && !all.equalsIgnoreCase("reset")) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID uuid = null;
                if (plugin.getDatabase().getUuidByUsername(playerName) != null)
                    uuid = UUID.fromString(plugin.getDatabase().getUuidByUsername(playerName));

                Player target = Bukkit.getPlayer(playerName);
                if (target != null && uuid == null) uuid = target.getUniqueId();

                if (uuid == null) {
                    sender.sendMessage(plugin.getMessageManager().get("player-not-found"));
                    return;
                }

                plugin.getDatabase().updatePurchasable(uuid, all.replace("reset", "null"));

                if (all.equalsIgnoreCase("reset")) {
                    sender.sendMessage(plugin.getMessageManager().get("purchased-reset-success", "%player%", playerName));
                    return;
                }

                String typeWord = all.equalsIgnoreCase("purchasable") ? " purchasable " : " ";
                sender.sendMessage(plugin.getMessageManager().get("purchased-give-success",
                        "%player%", playerName,
                        "%type%", typeWord));

            } catch (SQLException e) {
                sender.sendMessage(plugin.getMessageManager().get("db-error"));
                e.printStackTrace();
            }
        });
    }
}