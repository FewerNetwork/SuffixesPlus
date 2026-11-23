package me.zombieman.dev.suffixesplus;

import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseSettingsTest {
    @TempDir Path folder;
    YamlConfiguration settings() { var c = new YamlConfiguration(); c.set("database.url", "jdbc:mysql://example/suffix"); c.set("database.username", "test"); c.set("database.password", "test-value"); return c; }
    @Test void oldS13InlineConfigStillWorks() throws Exception {
        assertEquals("test", DatabaseSettings.load(folder, settings()).getString("username"));
    }
    @Test void privateFileOverridesVersionedSettings() throws Exception {
        var c = settings(); c.set("database.secrets-file", "database-credentials.yml");
        Files.writeString(folder.resolve("database-credentials.yml"), "url: jdbc:mysql://private/suffix\nusername: local\npassword: private-value\n");
        var s = DatabaseSettings.load(folder, c);
        assertEquals("local", s.getString("username")); assertEquals("private-value", s.getString("password"));
        assertEquals("test", c.getString("database.username"));
    }
    @Test void missingSecretsFileFailsWithoutFallingBackToPlaceholders() {
        var c = settings(); c.set("database.secrets-file", "missing.yml");
        assertThrows(java.io.IOException.class, () -> DatabaseSettings.load(folder, c));
    }
    @Test void privateFileCannotEscapeThePluginDirectory() {
        var c = settings(); c.set("database.secrets-file", "../other-plugin.yml");
        assertThrows(java.io.IOException.class, () -> DatabaseSettings.load(folder, c));
    }
}
