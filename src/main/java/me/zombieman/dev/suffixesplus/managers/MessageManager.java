package me.zombieman.dev.suffixesplus.managers;

import me.zombieman.dev.suffixesplus.SuffixesPlus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MessageManager {

    private final SuffixesPlus plugin;
    private FileConfiguration messages;

    public MessageManager(SuffixesPlus plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        String lang = plugin.getConfig().getString("language", "en");
        File langFolder = new File(plugin.getDataFolder(), "languages");

        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        saveBundledLanguageFiles();

        File langFile = new File(langFolder, lang + ".yml");

        if (!langFile.exists()) {
            plugin.getLogger().warning(
                    "Language file '" + lang + ".yml' not found. Falling back to en.yml."
            );

            langFile = new File(langFolder, "en.yml");

            if (!langFile.exists()) {
                throw new IllegalStateException(
                        "Could not find fallback language file en.yml"
                );
            }
        }

        messages = YamlConfiguration.loadConfiguration(langFile);

        try (InputStream defaultStream =
                     plugin.getResource("languages/" + langFile.getName())) {

            if (defaultStream != null) {
                FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
                );

                messages.setDefaults(defaults);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to load language defaults", ex);
        }

        plugin.getLogger().info("Loaded language: " + langFile.getName());
    }

    public Component get(String key, String... replacements) {
        String raw = resolve(key, replacements);
        return MiniMessage.miniMessage().deserialize(raw);
    }

    public String getRaw(String key, String... replacements) {
        return resolve(key, replacements);
    }

    public List<Component> getList(String key, String... replacements) {
        List<String> lines = messages.getStringList(key);
        return lines.stream()
                .map(line -> MiniMessage.miniMessage().deserialize(applyReplacements(line, replacements)))
                .collect(Collectors.toList());
    }

    private String resolve(String key, String... replacements) {
        String raw = messages.getString(key);
        if (raw == null) {
            plugin.getLogger().log(Level.WARNING, "Missing language key: ''{0}''", key);
            return "<red>[Missing message: " + key + "]";
        }
        return applyReplacements(raw, replacements);
    }

    private String applyReplacements(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    private void saveBundledLanguageFiles() {
        try {
            var codeSource = plugin.getClass()
                    .getProtectionDomain()
                    .getCodeSource();

            if (codeSource == null) {
                return;
            }

            try (java.util.jar.JarFile jar =
                         new java.util.jar.JarFile(
                                 new File(codeSource.getLocation().toURI()))) {

                jar.stream()
                        .filter(entry -> !entry.isDirectory())
                        .filter(entry -> entry.getName().startsWith("languages/"))
                        .filter(entry -> entry.getName().endsWith(".yml"))
                        .forEach(entry -> {
                            File destination = new File(
                                    plugin.getDataFolder(),
                                    entry.getName()
                            );

                            if (!destination.exists()) {
                                plugin.saveResource(entry.getName(), false);

                                plugin.getLogger().info(
                                        "Created language file: "
                                                + destination.getName()
                                );
                            }
                        });
            }
        } catch (Exception ex) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Failed to extract bundled language files",
                    ex
            );
        }
    }
}