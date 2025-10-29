package org.stepan.audio_disc.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles localization for the Audio Disc mod.
 */
public class Localization {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");
    private static final Gson GSON = new Gson();
    
    private static String defaultLanguage = "en_us";
    private static final Map<String, Map<String, String>> translations = new HashMap<>();
    
    /**
     * Loads translations for the specified language.
     * 
     * @param language Language code (e.g., "en_us", "ru_ru")
     */
    public static void loadLanguage(String language) {
        defaultLanguage = language.toLowerCase();
        loadLanguageInternal(defaultLanguage);
    }

    /**
     * Loads a specific language into the cache.
     * 
     * @param language Language code to load
     */
    private static void loadLanguageInternal(String language) {
        language = language.toLowerCase();
        
        // Skip if already loaded
        if (translations.containsKey(language)) {
            return;
        }
        
        Map<String, String> langMap = new HashMap<>();
        
        // First, load from mod resources (default translations)
        String resourcePath = "/assets/audio_disc/lang/" + language + ".json";
        try (var inputStream = Localization.class.getResourceAsStream(resourcePath)) {
            if (inputStream != null) {
                String json = new String(inputStream.readAllBytes());
                JsonObject langObj = GSON.fromJson(json, JsonObject.class);
                
                langObj.entrySet().forEach(entry -> {
                    langMap.put(entry.getKey(), entry.getValue().getAsString());
                });
                
                LOGGER.info("Loaded default language from resources: {}", language);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load language from resources: {}", resourcePath, e);
        }
        
        // Then, load from config directory (custom translations override defaults)
        Path configLangPath = FabricLoader.getInstance().getConfigDir()
            .resolve("audiodisc")
            .resolve("lang")
            .resolve(language + ".json");
        
        if (Files.exists(configLangPath)) {
            try {
                String json = Files.readString(configLangPath);
                JsonObject langObj = GSON.fromJson(json, JsonObject.class);
                
                // Override default translations with custom ones
                langObj.entrySet().forEach(entry -> {
                    langMap.put(entry.getKey(), entry.getValue().getAsString());
                });
                
                LOGGER.info("Loaded custom language overrides from config: {}", configLangPath);
            } catch (IOException e) {
                LOGGER.error("Failed to load custom language file: {}", configLangPath, e);
            }
        } else if (language.equals(defaultLanguage)) {
            // Create example custom language file only for default language
            createExampleLanguageFile(configLangPath, language);
        }
        
        // Store the final translations
        translations.put(language, langMap);
        LOGGER.info("Language loaded: {} ({} translations)", language, langMap.size());
    }
    
    /**
     * Gets a translated string for a specific language.
     * 
     * @param key Translation key
     * @param language Language code (e.g., "ru_ru", "en_us")
     * @return Translated string, or key if translation not found
     */
    public static String get(String key, String language) {
        language = language.toLowerCase();
        
        // Load language if not cached
        loadLanguageInternal(language);
        
        Map<String, String> langMap = translations.get(language);
        if (langMap != null && langMap.containsKey(key)) {
            return langMap.get(key);
        }
        
        // Fallback to English
        if (!language.equals("en_us")) {
            loadLanguageInternal("en_us");
            langMap = translations.get("en_us");
            if (langMap != null && langMap.containsKey(key)) {
                return langMap.get(key);
            }
        }
        
        return key;
    }

    /**
     * Gets a translated string using default language.
     * 
     * @param key Translation key
     * @return Translated string, or key if translation not found
     */
    public static String get(String key) {
        return get(key, defaultLanguage);
    }
    
    /**
     * Gets a translated string with formatting for a specific language.
     * 
     * @param key Translation key
     * @param language Language code
     * @param args Format arguments
     * @return Formatted translated string
     */
    public static String format(String key, String language, Object... args) {
        String template = get(key, language);
        return String.format(template, args);
    }

    /**
     * Gets a translated string with formatting using default language.
     * 
     * @param key Translation key
     * @param args Format arguments
     * @return Formatted translated string
     */
    public static String format(String key, Object... args) {
        return format(key, defaultLanguage, args);
    }
    
    /**
     * Creates an example custom language file for administrators.
     * 
     * @param path Path to create the file
     * @param language Language code
     */
    private static void createExampleLanguageFile(Path path, String language) {
        try {
            Files.createDirectories(path.getParent());
            
            // Create a minimal example file with comments
            Map<String, Object> example = new HashMap<>();
            example.put("_comment", "Custom language overrides for Audio Disc mod");
            example.put("_info", "Add any translation keys here to override default translations");
            example.put("_example_command.upload.success", "§a✓ Custom success message!");
            example.put("_example_command.upload.starting", "§aCustom loading message...");
            
            // Add a few common translations as examples
            if (language.equals("ru_ru")) {
                example.put("command.upload.success", "§a✓ Кастомное сообщение об успехе!");
                example.put("command.upload.starting", "§aКастомное сообщение загрузки...");
            } else {
                example.put("command.upload.success", "§a✓ Custom success message!");
                example.put("command.upload.starting", "§aCustom loading message...");
            }
            
            String json = GSON.toJson(example);
            Files.writeString(path, json);
            LOGGER.info("Created example custom language file: {}", path);
            
        } catch (IOException e) {
            LOGGER.error("Failed to create example language file: {}", path, e);
        }
    }

    /**
     * Creates a default language file (legacy method).
     * 
     * @param path Path to create the file
     * @param language Language code
     */
    private static void createDefaultLanguageFile(Path path, String language) {
        try {
            Files.createDirectories(path.getParent());
            
            Map<String, String> defaults = getDefaultTranslations(language);
            String json = GSON.toJson(defaults);
            
            Files.writeString(path, json);
            LOGGER.info("Created default language file: {}", path);
            
        } catch (IOException e) {
            LOGGER.error("Failed to create default language file: {}", path, e);
        }
    }
    
    /**
     * Gets the language code for a player.
     * 
     * @param player The player to get language for
     * @return Language code (e.g., "ru_ru", "en_us")
     */
    public static String getPlayerLanguage(net.minecraft.server.network.ServerPlayerEntity player) {
        if (player == null) {
            return defaultLanguage;
        }
        
        // Get the player's language from their client settings
        String clientLanguage = player.getClientOptions().language();
        
        // Convert to lowercase and ensure it's valid
        if (clientLanguage != null && !clientLanguage.isEmpty()) {
            return clientLanguage.toLowerCase();
        }
        
        return defaultLanguage;
    }

    /**
     * Gets a translated string for a specific player.
     * 
     * @param key Translation key
     * @param player The player to get translation for
     * @return Translated string in player's language
     */
    public static String getForPlayer(String key, net.minecraft.server.network.ServerPlayerEntity player) {
        String playerLanguage = getPlayerLanguage(player);
        return get(key, playerLanguage);
    }

    /**
     * Gets a formatted translated string for a specific player.
     * 
     * @param key Translation key
     * @param player The player to get translation for
     * @param args Format arguments
     * @return Formatted translated string in player's language
     */
    public static String formatForPlayer(String key, net.minecraft.server.network.ServerPlayerEntity player, Object... args) {
        String playerLanguage = getPlayerLanguage(player);
        return format(key, playerLanguage, args);
    }

    /**
     * Gets the Simple Voice Chat category name for localization.
     * 
     * @return The category name
     */
    public static String getVoiceChatCategory() {
        return get("voicechat.category");
    }

    /**
     * Creates full language files in config directory for customization.
     * This method can be called by admin commands to generate complete translation files.
     * 
     * @param language Language code to create
     * @return true if successful, false otherwise
     */
    public static boolean createFullLanguageFile(String language) {
        try {
            Path configLangPath = FabricLoader.getInstance().getConfigDir()
                .resolve("audiodisc")
                .resolve("lang")
                .resolve(language.toLowerCase() + ".json");
            
            Files.createDirectories(configLangPath.getParent());
            
            // Load default translations from resources
            Map<String, String> fullTranslations = new HashMap<>();
            String resourcePath = "/assets/audio_disc/lang/" + language.toLowerCase() + ".json";
            
            try (var inputStream = Localization.class.getResourceAsStream(resourcePath)) {
                if (inputStream != null) {
                    String json = new String(inputStream.readAllBytes());
                    JsonObject langObj = GSON.fromJson(json, JsonObject.class);
                    
                    for (var entry : langObj.entrySet()) {
                        fullTranslations.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            
            // If no resource found, use default translations
            if (fullTranslations.isEmpty()) {
                fullTranslations = getDefaultTranslations(language.toLowerCase());
            }
            
            // Add header comment
            Map<String, Object> fileContent = new HashMap<>();
            fileContent.put("_comment", "Custom language file for Audio Disc mod - Language: " + language);
            fileContent.put("_info", "Modify any translation below to customize messages");
            fileContent.put("_note", "Remove the underscore prefix from _comment, _info, _note to use them as actual translations");
            
            // Add all translations
            fullTranslations.forEach(fileContent::put);
            
            String json = GSON.toJson(fileContent);
            Files.writeString(configLangPath, json);
            
            LOGGER.info("Created full custom language file: {}", configLangPath);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to create full language file for {}", language, e);
            return false;
        }
    }
    
    /**
     * Gets default translations for a language.
     * 
     * @param language Language code
     * @return Map of translations
     */
    private static Map<String, String> getDefaultTranslations(String language) {
        Map<String, String> translations = new HashMap<>();
        
        if (language.equals("ru_ru")) {
            // Russian translations
            translations.put("command.upload.no_disc", "§cВы должны держать музыкальный диск!");
            translations.put("command.upload.no_permission", "§cУ вас нет прав на использование этой команды!");
            translations.put("command.upload.rate_limit", "§c%s");
            translations.put("command.upload.invalid_url", "§cПожалуйста, укажите корректный URL!");
            translations.put("command.upload.url_protocol", "§cURL должен начинаться с http:// или https://");
            translations.put("command.upload.starting", "§aНачинается загрузка аудио...");
            translations.put("command.upload.url_info", "§7URL: %s");
            translations.put("command.upload.progress", "§7Прогресс загрузки: %d%%");
            translations.put("command.upload.complete", "§aЗагрузка завершена! Размер: %s");
            translations.put("command.upload.processing", "§7Обработка аудио файла...");
            translations.put("command.upload.success", "§a✓ Аудио успешно загружено на диск!");
            translations.put("command.upload.format", "§7Формат: %s");
            translations.put("command.upload.duration", "§7Длительность: %s");
            translations.put("command.upload.duration_exceeded", "§cДлительность аудио превышает максимально допустимую (%s)!");
            translations.put("command.upload.bitrate", "§7Битрейт: %d kbps");
            translations.put("command.upload.error", "§cОшибка обработки аудио: %s");
            translations.put("command.upload.download_failed", "§cЗагрузка не удалась: %s");
            translations.put("command.upload.timeout", "§7Загрузка заняла слишком много времени (макс. 30 секунд)");
            translations.put("command.upload.too_large", "§7Максимальный размер файла 50MB");
            translations.put("command.upload.unreachable", "§7URL недоступен");
            
            translations.put("command.rename.no_disc", "§cВы должны держать музыкальный диск!");
            translations.put("command.rename.no_audio", "§cНа этом диске нет кастомного аудио! Сначала загрузите аудио с помощью /audiodisc upload <url>");
            translations.put("command.rename.too_long", "§cНазвание слишком длинное! Максимум 100 символов.");
            translations.put("command.rename.success", "§a✓ Диск успешно переименован!");
            translations.put("command.rename.new_title", "§7Новое название: §f%s");
            translations.put("command.rename.error", "§cОшибка переименования диска: %s");
            
            translations.put("command.info.no_disc", "§cВы должны держать музыкальный диск!");
            translations.put("command.info.no_audio", "§cНа этом диске нет кастомного аудио!");
            translations.put("command.info.header", "§a=== Информация об аудио ===");
            translations.put("command.info.id", "§7ID: §f%s");
            translations.put("command.info.title", "§7Название: §f%s");
            translations.put("command.info.format", "§7Формат: §f%s");
            translations.put("command.info.duration", "§7Длительность: §f%s");
            translations.put("command.info.bitrate", "§7Битрейт: §f%d kbps");
            translations.put("command.info.sample_rate", "§7Частота дискретизации: §f%d Hz");
            translations.put("command.info.uploaded_by", "§7Загрузил: §f%s");
            translations.put("command.info.upload_time", "§7Время загрузки: §f%s");
            translations.put("command.info.file_size", "§7Размер файла: §f%s");
            
            translations.put("command.clear.no_disc", "§cВы должны держать музыкальный диск!");
            translations.put("command.clear.no_audio", "§cНа этом диске нет кастомного аудио!");
            translations.put("command.clear.success", "§aАудио успешно удалено с диска!");
            translations.put("command.clear.failed", "§cНе удалось удалить аудио");
            translations.put("command.clear.error", "§cОшибка удаления аудио: %s");
            
            translations.put("command.reload.success", "§aКонфигурация успешно перезагружена!");
            translations.put("command.reload.failed", "§cНе удалось перезагрузить конфигурацию: %s");
            
            translations.put("tooltip.custom_disc", "♪ Кастомный музыкальный диск");
            translations.put("tooltip.title", "Название: %s");
            translations.put("tooltip.duration", "Длительность: %s");
            translations.put("tooltip.format", "Формат: %s");
            translations.put("tooltip.uploaded_by", "Загрузил: %s");
            
            translations.put("voicechat.category", "Музыкальные диски");
            
            // YouTube command translations
            translations.put("command.youtube.invalid_url", "§cПожалуйста, укажите корректную YouTube ссылку!");
            translations.put("command.youtube.supported_formats", "§7Поддерживаются ссылки: youtube.com/watch?v=..., youtu.be/..., m.youtube.com/watch?v=...");
            translations.put("command.youtube.starting", "§aНачинается загрузка аудио с YouTube...");
            translations.put("command.youtube.processing", "§7Извлечение аудио из видео...");
            translations.put("command.youtube.wait", "§7Это может занять некоторое время...");
            translations.put("command.youtube.success", "§a✓ Аудио с YouTube успешно загружено на диск!");
            translations.put("command.youtube.error", "§cОшибка загрузки с YouTube: %s");
            translations.put("command.youtube.not_found", "§cОшибка: загруженный файл не найден");
            translations.put("command.youtube.yt_dlp_required", "§7Для работы с YouTube необходимо установить yt-dlp на сервере");
            translations.put("command.youtube.use_direct", "§7Используйте обычную команду /audiodisc upload <прямая_ссылка_на_аудио>");
            
        } else {
            // English translations (default)
            translations.put("command.upload.no_disc", "§cYou must be holding a music disc!");
            translations.put("command.upload.no_permission", "§cYou don't have permission to use this command!");
            translations.put("command.upload.rate_limit", "§c%s");
            translations.put("command.upload.invalid_url", "§cPlease provide a valid URL!");
            translations.put("command.upload.url_protocol", "§cURL must start with http:// or https://");
            translations.put("command.upload.starting", "§aStarting audio download...");
            translations.put("command.upload.url_info", "§7URL: %s");
            translations.put("command.upload.progress", "§7Download progress: %d%%");
            translations.put("command.upload.complete", "§aDownload complete! Size: %s");
            translations.put("command.upload.processing", "§7Processing audio file...");
            translations.put("command.upload.success", "§a✓ Audio successfully uploaded to disc!");
            translations.put("command.upload.format", "§7Format: %s");
            translations.put("command.upload.duration", "§7Duration: %s");
            translations.put("command.upload.duration_exceeded", "§cAudio duration exceeds maximum allowed (%s)!");
            translations.put("command.upload.bitrate", "§7Bitrate: %d kbps");
            translations.put("command.upload.error", "§cError processing audio: %s");
            translations.put("command.upload.download_failed", "§cDownload failed: %s");
            translations.put("command.upload.timeout", "§7The download took too long (max 30 seconds)");
            translations.put("command.upload.too_large", "§7Maximum file size is 50MB");
            translations.put("command.upload.unreachable", "§7The URL could not be reached");
            
            translations.put("command.rename.no_disc", "§cYou must be holding a music disc!");
            translations.put("command.rename.no_audio", "§cThis disc doesn't have custom audio! Upload audio first with /audiodisc upload <url>");
            translations.put("command.rename.too_long", "§cTitle is too long! Maximum 100 characters.");
            translations.put("command.rename.success", "§a✓ Disc renamed successfully!");
            translations.put("command.rename.new_title", "§7New title: §f%s");
            translations.put("command.rename.error", "§cError renaming disc: %s");
            
            translations.put("command.info.no_disc", "§cYou must be holding a music disc!");
            translations.put("command.info.no_audio", "§cThis disc doesn't have custom audio!");
            translations.put("command.info.header", "§a=== Audio Information ===");
            translations.put("command.info.id", "§7ID: §f%s");
            translations.put("command.info.title", "§7Title: §f%s");
            translations.put("command.info.format", "§7Format: §f%s");
            translations.put("command.info.duration", "§7Duration: §f%s");
            translations.put("command.info.bitrate", "§7Bitrate: §f%d kbps");
            translations.put("command.info.sample_rate", "§7Sample Rate: §f%d Hz");
            translations.put("command.info.uploaded_by", "§7Uploaded by: §f%s");
            translations.put("command.info.upload_time", "§7Upload time: §f%s");
            translations.put("command.info.file_size", "§7File size: §f%s");
            
            translations.put("command.clear.no_disc", "§cYou must be holding a music disc!");
            translations.put("command.clear.no_audio", "§cThis disc doesn't have custom audio!");
            translations.put("command.clear.success", "§aSuccessfully cleared audio from disc!");
            translations.put("command.clear.failed", "§cFailed to clear audio");
            translations.put("command.clear.error", "§cError clearing audio: %s");
            
            translations.put("command.reload.success", "§aConfiguration reloaded successfully!");
            translations.put("command.reload.failed", "§cFailed to reload configuration: %s");
            
            translations.put("tooltip.custom_disc", "♪ Custom Audio Disc");
            translations.put("tooltip.title", "Title: %s");
            translations.put("tooltip.duration", "Duration: %s");
            translations.put("tooltip.format", "Format: %s");
            translations.put("tooltip.uploaded_by", "Uploaded by: %s");
            
            translations.put("voicechat.category", "Audio Discs");
            
            // YouTube command translations
            translations.put("command.youtube.invalid_url", "§cPlease provide a valid YouTube URL!");
            translations.put("command.youtube.supported_formats", "§7Supported URLs: youtube.com/watch?v=..., youtu.be/..., m.youtube.com/watch?v=...");
            translations.put("command.youtube.starting", "§aStarting YouTube audio download...");
            translations.put("command.youtube.processing", "§7Extracting audio from video...");
            translations.put("command.youtube.wait", "§7This may take some time...");
            translations.put("command.youtube.success", "§a✓ YouTube audio successfully uploaded to disc!");
            translations.put("command.youtube.error", "§cYouTube download error: %s");
            translations.put("command.youtube.not_found", "§cError: downloaded file not found");
            translations.put("command.youtube.yt_dlp_required", "§7yt-dlp is required on the server for YouTube support");
            translations.put("command.youtube.use_direct", "§7Use regular command /audiodisc upload <direct_audio_url>");
        }
        
        return translations;
    }
}
