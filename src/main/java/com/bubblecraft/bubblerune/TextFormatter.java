package com.bubblecraft.bubblerune;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for formatting text with support for multiple color formats:
 * - Legacy: &a, &l, &o, etc.
 * - Hex RGB: &#rrggbb, &x&r&r&g&g&b&b, §x§r§r§g§g§b§b, <#rrggbb>
 * - MiniMessage: <green>, <bold>, <gradient>, <rainbow>, etc.
 * - JSON: Full Adventure component JSON
 */
public class TextFormatter {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    // Use section symbol serializer for proper Minecraft rendering
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    
    // Regex patterns for various hex color formats
    private static final Pattern HEX_AMPERSAND_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Pattern HEX_AMPERSAND_X_PATTERN = Pattern.compile("&x(?:&([0-9A-Fa-f]))(?:&([0-9A-Fa-f]))(?:&([0-9A-Fa-f]))(?:&([0-9A-Fa-f]))(?:&([0-9A-Fa-f]))(?:&([0-9A-Fa-f]))");
    private static final Pattern HEX_SECTION_X_PATTERN = Pattern.compile("§x(?:§([0-9A-Fa-f]))(?:§([0-9A-Fa-f]))(?:§([0-9A-Fa-f]))(?:§([0-9A-Fa-f]))(?:§([0-9A-Fa-f]))(?:§([0-9A-Fa-f]))");
    private static final Pattern MINIMESSAGE_HEX_PATTERN = Pattern.compile("<#([0-9A-Fa-f]{6})>");
    
    /**
     * Formats a string supporting multiple color code formats:
     * - &#rrggbb (hex with ampersand)
     * - &x&r&r&g&g&b&b (hex with ampersand x)
     * - §x§r§r§g§g§b§b (hex with section x)
     * - <#rrggbb> (MiniMessage hex)
     * - <color> tags (MiniMessage)
     * - &a, &l, etc. (legacy)
     * 
     * @param text The text to format
     * @return Formatted string with § color codes for Minecraft
     */
    public static String format(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Convert all hex formats to MiniMessage hex format for uniform processing
        text = convertHexFormatsToMiniMessage(text);
        
        // Convert legacy &codes to MiniMessage equivalents for uniform processing
        text = convertLegacyToMiniMessage(text);
        
        // If text contains MiniMessage tags, parse as MiniMessage and serialize to section codes
        if (text.contains("<") && text.contains(">")) {
            try {
                Component component = MINI_MESSAGE.deserialize(text);
                return LEGACY_SERIALIZER.serialize(component);
            } catch (Exception e) {
                // Fall back to simple translation if MiniMessage parsing fails
            }
        }
        
        // Otherwise treat as legacy color codes - translate & to §
        return text.replace('&', '§');
    }
    
    /**
     * Converts legacy &codes to MiniMessage tags
     */
    private static String convertLegacyToMiniMessage(String text) {
        // Convert legacy color codes to MiniMessage
        text = text.replace("&0", "<black>");
        text = text.replace("&1", "<dark_blue>");
        text = text.replace("&2", "<dark_green>");
        text = text.replace("&3", "<dark_aqua>");
        text = text.replace("&4", "<dark_red>");
        text = text.replace("&5", "<dark_purple>");
        text = text.replace("&6", "<gold>");
        text = text.replace("&7", "<gray>");
        text = text.replace("&8", "<dark_gray>");
        text = text.replace("&9", "<blue>");
        text = text.replace("&a", "<green>");
        text = text.replace("&A", "<green>");
        text = text.replace("&b", "<aqua>");
        text = text.replace("&B", "<aqua>");
        text = text.replace("&c", "<red>");
        text = text.replace("&C", "<red>");
        text = text.replace("&d", "<light_purple>");
        text = text.replace("&D", "<light_purple>");
        text = text.replace("&e", "<yellow>");
        text = text.replace("&E", "<yellow>");
        text = text.replace("&f", "<white>");
        text = text.replace("&F", "<white>");
        
        // Convert formatting codes
        text = text.replace("&l", "<bold>");
        text = text.replace("&L", "<bold>");
        text = text.replace("&o", "<italic>");
        text = text.replace("&O", "<italic>");
        text = text.replace("&n", "<underlined>");
        text = text.replace("&N", "<underlined>");
        text = text.replace("&m", "<strikethrough>");
        text = text.replace("&M", "<strikethrough>");
        text = text.replace("&k", "<obfuscated>");
        text = text.replace("&K", "<obfuscated>");
        text = text.replace("&r", "<reset>");
        text = text.replace("&R", "<reset>");
        
        return text;
    }
    
    /**
     * Converts all hex color formats to MiniMessage format
     */
    private static String convertHexFormatsToMiniMessage(String text) {
        // Convert &#rrggbb to <#rrggbb>
        text = HEX_AMPERSAND_PATTERN.matcher(text).replaceAll("<#$1>");
        
        // Convert &x&r&r&g&g&b&b to <#rrggbb>
        Matcher hexAmpersandX = HEX_AMPERSAND_X_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (hexAmpersandX.find()) {
            String hex = hexAmpersandX.group(1) + hexAmpersandX.group(2) + 
                        hexAmpersandX.group(3) + hexAmpersandX.group(4) + 
                        hexAmpersandX.group(5) + hexAmpersandX.group(6);
            hexAmpersandX.appendReplacement(sb, "<#" + hex + ">");
        }
        hexAmpersandX.appendTail(sb);
        text = sb.toString();
        
        // Convert §x§r§r§g§g§b§b to <#rrggbb>
        Matcher hexSectionX = HEX_SECTION_X_PATTERN.matcher(text);
        sb = new StringBuffer();
        while (hexSectionX.find()) {
            String hex = hexSectionX.group(1) + hexSectionX.group(2) + 
                        hexSectionX.group(3) + hexSectionX.group(4) + 
                        hexSectionX.group(5) + hexSectionX.group(6);
            hexSectionX.appendReplacement(sb, "<#" + hex + ">");
        }
        hexSectionX.appendTail(sb);
        text = sb.toString();
        
        return text;
    }
    
    /**
     * Formats a list of strings
     * @param lines List of text lines to format
     * @return List of formatted strings
     */
    public static List<String> format(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        
        List<String> formatted = new ArrayList<>();
        for (String line : lines) {
            formatted.add(format(line));
        }
        return formatted;
    }
    
    /**
     * Converts formatted text to an Adventure Component
     * @param text The text to convert
     * @return Adventure Component
     */
    public static Component toComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        
        // Convert hex formats to MiniMessage
        text = convertHexFormatsToMiniMessage(text);
        
        // Convert legacy &codes to MiniMessage
        text = convertLegacyToMiniMessage(text);
        
        // Parse as MiniMessage (all formats now converted)
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception e) {
            // Fall back to plain text component
            return Component.text(text);
        }
    }
    
    /**
     * Checks if a string uses MiniMessage format
     * @param text The text to check
     * @return true if text appears to use MiniMessage format
     */
    public static boolean isMiniMessage(String text) {
        return text != null && text.contains("<") && text.contains(">");
    }
    
    /**
     * Checks if a string uses any hex color format
     * @param text The text to check
     * @return true if text contains hex colors
     */
    public static boolean hasHexColors(String text) {
        if (text == null) return false;
        return HEX_AMPERSAND_PATTERN.matcher(text).find() ||
               HEX_AMPERSAND_X_PATTERN.matcher(text).find() ||
               HEX_SECTION_X_PATTERN.matcher(text).find() ||
               MINIMESSAGE_HEX_PATTERN.matcher(text).find();
    }
}
