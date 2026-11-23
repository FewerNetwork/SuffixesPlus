package me.zombieman.dev.suffixesplus;

import java.io.IOException;
import java.nio.file.Path;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Optional local secrets file lets public settings be versioned without passwords. */
final class DatabaseSettings {
    static ConfigurationSection load(Path folder, ConfigurationSection config) throws IOException {
        String file = config.getString("database.secrets-file");
        ConfigurationSection settings = config.getConfigurationSection("database");
        if (file != null && !file.isBlank()) {
            Path root = folder.toAbsolutePath().normalize();
            Path target = root.resolve(file).normalize();
            if (!target.startsWith(root) || target.equals(root)) throw new IOException("Secrets file must stay inside the plugin folder");
            var local = new YamlConfiguration();
            try { local.load(target.toFile()); }
            catch (Exception exception) { throw new IOException("Configured secrets file is missing or invalid", exception); }
            settings = local;
        }
        if (settings == null) throw new IOException("Database settings are missing");
        for (String field : new String[]{"url", "username", "password"})
            if (!settings.isString(field) || settings.getString(field).isBlank()) throw new IOException("Database field is missing: " + field);
        return settings;
    }
}
