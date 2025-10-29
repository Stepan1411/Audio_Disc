package org.stepan.audio_disc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Configuration class for the Audio Disc mod.
 * Handles loading and saving configuration from/to audiodisc.json.
 */
public class AudioDiscConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Configuration fields with default values
    private long maxFileSize = 52428800; // 50MB in bytes
    private int downloadTimeout = 30; // seconds
    private long maxDuration = -1; // -1 = unlimited, otherwise in milliseconds
    private List<String> supportedFormats = List.of("mp3", "ogg", "wav");
    private boolean enableProgressUpdates = true;
    private int progressUpdateInterval = 25; // percentage
    private String storageDirectory = "audiodisc/audio";
    private boolean enableApiEvents = true;
    private String language = "en_us"; // Default language
    private double audioRange = 64.0; // Audio range in blocks (default jukebox range)
    private boolean autoInstallYtDlp = true; // Automatically install yt-dlp on server start
    private boolean autoInstallFFmpeg = true; // Automatically install FFmpeg on server start
    
    /**
     * Loads configuration from the specified path.
     * If the file doesn't exist, creates a new one with default values.
     * 
     * @param configPath Path to the configuration file
     * @return Loaded or default configuration
     */
    public static AudioDiscConfig load(Path configPath) {
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                AudioDiscConfig config = GSON.fromJson(json, AudioDiscConfig.class);
                LOGGER.info("Loaded configuration from {}", configPath);
                return config;
            } catch (IOException e) {
                LOGGER.error("Failed to load configuration from {}, using defaults", configPath, e);
                return createDefault(configPath);
            }
        } else {
            LOGGER.info("Configuration file not found, creating default at {}", configPath);
            return createDefault(configPath);
        }
    }
    
    /**
     * Creates a default configuration and saves it to the specified path.
     * 
     * @param configPath Path where the configuration should be saved
     * @return Default configuration
     */
    private static AudioDiscConfig createDefault(Path configPath) {
        AudioDiscConfig config = new AudioDiscConfig();
        config.save(configPath);
        return config;
    }
    
    /**
     * Saves the current configuration to the specified path.
     * 
     * @param configPath Path where the configuration should be saved
     */
    public void save(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(this);
            Files.writeString(configPath, json);
            LOGGER.info("Saved configuration to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration to {}", configPath, e);
        }
    }
    
    /**
     * Validates the configuration values.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean validate() {
        if (maxFileSize <= 0 || maxFileSize > 104857600) { // Max 100MB
            LOGGER.warn("Invalid maxFileSize: {}. Must be between 1 and 104857600 bytes", maxFileSize);
            return false;
        }
        
        if (downloadTimeout <= 0 || downloadTimeout > 300) { // Max 5 minutes
            LOGGER.warn("Invalid downloadTimeout: {}. Must be between 1 and 300 seconds", downloadTimeout);
            return false;
        }
        
        if (maxDuration != -1 && maxDuration <= 0) {
            LOGGER.warn("Invalid maxDuration: {}. Must be -1 (unlimited) or positive value in milliseconds", maxDuration);
            return false;
        }
        
        if (supportedFormats == null || supportedFormats.isEmpty()) {
            LOGGER.warn("supportedFormats cannot be empty");
            return false;
        }
        
        if (progressUpdateInterval <= 0 || progressUpdateInterval > 100) {
            LOGGER.warn("Invalid progressUpdateInterval: {}. Must be between 1 and 100", progressUpdateInterval);
            return false;
        }
        
        if (storageDirectory == null || storageDirectory.isBlank()) {
            LOGGER.warn("storageDirectory cannot be empty");
            return false;
        }
        
        if (language == null || language.isBlank()) {
            LOGGER.warn("language cannot be empty");
            return false;
        }
        
        if (audioRange <= 0 || audioRange > 1000) {
            LOGGER.warn("Invalid audioRange: {}. Must be between 1 and 1000 blocks", audioRange);
            return false;
        }
        
        return true;
    }
    
    // Getters
    public long getMaxFileSize() {
        return maxFileSize;
    }
    
    public int getDownloadTimeout() {
        return downloadTimeout;
    }
    
    public long getMaxDuration() {
        return maxDuration;
    }
    
    public List<String> getSupportedFormats() {
        return supportedFormats;
    }
    
    public boolean isEnableProgressUpdates() {
        return enableProgressUpdates;
    }
    
    public int getProgressUpdateInterval() {
        return progressUpdateInterval;
    }
    
    public String getStorageDirectory() {
        return storageDirectory;
    }
    
    public boolean isEnableApiEvents() {
        return enableApiEvents;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public double getAudioRange() {
        return audioRange;
    }
    
    public boolean isAutoInstallYtDlp() {
        return autoInstallYtDlp;
    }
    
    public boolean isAutoInstallFFmpeg() {
        return autoInstallFFmpeg;
    }
}
