package me.zombieman.dev.suffixesplus.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("[&§]#([A-Fa-f0-9]{6})");

    private static final Pattern BUNGEE_HEX_PATTERN = Pattern.compile(
            "§[xX]§([0-9A-Fa-f])§([0-9A-Fa-f])§([0-9A-Fa-f])§([0-9A-Fa-f])§([0-9A-Fa-f])§([0-9A-Fa-f])");

    private static final Pattern LEGACY_PATTERN = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");

    public static Component parseLegacyColors(String message) {
        if (message == null) {
            return Component.empty();
        }

        String converted = convertBungeeHexCodes(message);
        converted = convertHexCodes(converted);
        converted = convertLegacyCodes(converted);

        return MiniMessage.miniMessage().deserialize(converted);
    }

    private static String convertBungeeHexCodes(String message) {
        Matcher matcher = BUNGEE_HEX_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1) + matcher.group(2) + matcher.group(3)
                    + matcher.group(4) + matcher.group(5) + matcher.group(6);
            matcher.appendReplacement(result, Matcher.quoteReplacement("<#" + hex + ">"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String convertHexCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("<#" + matcher.group(1) + ">"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String convertLegacyCodes(String message) {
        Matcher matcher = LEGACY_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String tag = legacyTag(matcher.group(1).toLowerCase());
            String replacement = tag != null ? tag : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String legacyTag(String code) {
        return switch (code) {
            case "0" -> "<black>";
            case "1" -> "<dark_blue>";
            case "2" -> "<dark_green>";
            case "3" -> "<dark_aqua>";
            case "4" -> "<dark_red>";
            case "5" -> "<dark_purple>";
            case "6" -> "<gold>";
            case "7" -> "<gray>";
            case "8" -> "<dark_gray>";
            case "9" -> "<blue>";
            case "a" -> "<green>";
            case "b" -> "<aqua>";
            case "c" -> "<red>";
            case "d" -> "<light_purple>";
            case "e" -> "<yellow>";
            case "f" -> "<white>";
            case "k" -> "<obfuscated>";
            case "l" -> "<bold>";
            case "m" -> "<strikethrough>";
            case "n" -> "<underlined>";
            case "o" -> "<italic>";
            case "r" -> "<reset>";
            default -> null;
        };
    }
}