package org.stepan.audio_disc;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stepan.audio_disc.api.AudioDiscAPI;
import org.stepan.audio_disc.api.AudioDiscAPIImpl;
import org.stepan.audio_disc.command.AudioDiscCommand;
import org.stepan.audio_disc.config.AudioDiscConfig;
import org.stepan.audio_disc.download.AudioDownloadManager;
import org.stepan.audio_disc.playback.PlaybackManager;
import org.stepan.audio_disc.playback.SimpleVoiceChatIntegration;
import org.stepan.audio_disc.processing.AudioProcessor;
import org.stepan.audio_disc.storage.AudioStorageManager;

import java.nio.file.Path;

public class Audio_disc implements ModInitializer {
    public static final String MOD_ID = "audio_disc";
    public static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");
    
    private static AudioDiscConfig config;
    private static AudioDownloadManager downloadManager;
    private static AudioProcessor audioProcessor;
    private static AudioStorageManager storageManager;
    private static PlaybackManager playbackManager;
    private static SimpleVoiceChatIntegration voiceChatIntegration;
    private static org.stepan.audio_disc.util.RateLimiter rateLimiter;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Audio Disc mod");
        
        // Load configuration
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("audiodisc").resolve("config.json");
        config = AudioDiscConfig.load(configPath);
        
        if (!config.validate()) {
            LOGGER.error("Configuration validation failed. Please check your config.json file.");
        } else {
            LOGGER.info("Configuration loaded successfully");
        }
        
        // Load localization
        org.stepan.audio_disc.util.Localization.loadLanguage(config.getLanguage());
        LOGGER.info("Localization loaded for language: {}", config.getLanguage());
        
        // Test audio libraries
        testAudioLibraries();
        
        // Initialize managers
        try {
            // Initialize download manager
            downloadManager = new AudioDownloadManager(
                config.getMaxFileSize(),
                config.getDownloadTimeout(),
                5 // max concurrent downloads
            );
            LOGGER.info("AudioDownloadManager initialized");
            
            // Initialize audio processor
            audioProcessor = new AudioProcessor();
            LOGGER.info("AudioProcessor initialized");
            
            // Initialize storage manager
            Path storageDir = FabricLoader.getInstance().getGameDir()
                .resolve(config.getStorageDirectory());
            storageManager = new AudioStorageManager(storageDir);
            LOGGER.info("AudioStorageManager initialized");
            
            // Initialize Simple Voice Chat integration
            // Create instance first (will be used by Voice Chat plugin system)
            voiceChatIntegration = new SimpleVoiceChatIntegration();
            voiceChatIntegration.setConfig(config);
            
            // Check if Simple Voice Chat is loaded
            boolean voiceChatLoaded = FabricLoader.getInstance().isModLoaded("voicechat");
            if (voiceChatLoaded) {
                LOGGER.info("Simple Voice Chat mod detected");
                LOGGER.info("Voice Chat will initialize the plugin automatically");
            } else {
                LOGGER.warn("Simple Voice Chat mod not found! Custom audio playback will not work.");
                LOGGER.warn("Please install Simple Voice Chat: https://modrinth.com/plugin/simple-voice-chat");
            }
            
            LOGGER.info("SimpleVoiceChatIntegration created");
            
            // Initialize playback manager
            playbackManager = new PlaybackManager(voiceChatIntegration, storageManager);
            LOGGER.info("PlaybackManager initialized");
            
            // Initialize rate limiter (3 uploads per minute, 10 second cooldown)
            rateLimiter = new org.stepan.audio_disc.util.RateLimiter(3, 10);
            LOGGER.info("RateLimiter initialized");
            
            // Initialize API
            AudioDiscAPIImpl.getInstance();
            LOGGER.info("API initialized and ready for addons");
            
            // Register commands
            CommandRegistrationCallback.EVENT.register(AudioDiscCommand::register);
            LOGGER.info("Commands registered");
            
            // Register jukebox event handlers
            org.stepan.audio_disc.events.JukeboxEventHandler.register();
            LOGGER.info("Jukebox event handlers registered");
            
            // Initialize yt-dlp automatically on server start
            initializeYtDlp();
            
            // Initialize FFmpeg automatically on server start
            initializeFFmpeg();
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutting down Audio Disc mod");
                if (downloadManager != null) {
                    downloadManager.shutdown();
                }
                if (playbackManager != null) {
                    playbackManager.shutdown();
                }
                if (voiceChatIntegration != null) {
                    voiceChatIntegration.stopAllStreams();
                }
            }));
            
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Audio Disc mod", e);
        }
        
        LOGGER.info("Audio Disc mod initialized");
    }
    
    /**
     * Gets the current configuration instance.
     * 
     * @return The loaded configuration
     */
    public static AudioDiscConfig getConfig() {
        return config;
    }

    /**
     * Gets the download manager instance.
     * 
     * @return The download manager
     */
    public static AudioDownloadManager getDownloadManager() {
        return downloadManager;
    }

    /**
     * Gets the audio processor instance.
     * 
     * @return The audio processor
     */
    public static AudioProcessor getAudioProcessor() {
        return audioProcessor;
    }

    /**
     * Gets the storage manager instance.
     * 
     * @return The storage manager
     */
    public static AudioStorageManager getStorageManager() {
        return storageManager;
    }

    /**
     * Gets the playback manager instance.
     * 
     * @return The playback manager
     */
    public static PlaybackManager getPlaybackManager() {
        return playbackManager;
    }

    /**
     * Gets the Simple Voice Chat integration instance.
     * 
     * @return The voice chat integration
     */
    public static SimpleVoiceChatIntegration getVoiceChatIntegration() {
        // Return singleton instance (may be different from voiceChatIntegration if Voice Chat created new instance)
        SimpleVoiceChatIntegration singleton = SimpleVoiceChatIntegration.getInstance();
        return singleton != null ? singleton : voiceChatIntegration;
    }

    /**
     * Gets the rate limiter instance.
     * 
     * @return The rate limiter
     */
    public static org.stepan.audio_disc.util.RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    /**
     * Gets the public API instance for addon developers.
     * 
     * @return The API instance
     */
    public static AudioDiscAPI getAPI() {
        return AudioDiscAPIImpl.getInstance();
    }

    /**
     * Updates the configuration.
     * 
     * @param newConfig The new configuration to apply
     */
    public static void updateConfig(AudioDiscConfig newConfig) {
        config = newConfig;
        LOGGER.info("Configuration updated");
    }
    
    /**
     * Initializes yt-dlp automatically on server start.
     */
    private static void initializeYtDlp() {
        try {
            // Check if auto-install is enabled in config
            if (!config.isAutoInstallYtDlp()) {
                LOGGER.info("Automatic yt-dlp installation is disabled in config");
                LOGGER.info("Use /audiodisc ytdlp install to install manually if needed");
                LOGGER.info("YouTube functionality will only work if yt-dlp is manually installed");
                return;
            }
            
            LOGGER.info("Checking yt-dlp availability on server startup...");
            
            // Check if yt-dlp is already available
            if (org.stepan.audio_disc.download.YtDlpManager.isAvailable()) {
                String executablePath = org.stepan.audio_disc.download.YtDlpManager.getExecutablePath();
                LOGGER.info("yt-dlp is already available at: {}", executablePath);
                LOGGER.info("YouTube functionality is ready!");
                
                long size = org.stepan.audio_disc.download.YtDlpManager.getInstallationSize();
                if (size > 0) {
                    LOGGER.info("Installation size: {}", formatBytes(size));
                }
                return;
            }
            
            // If not available, start automatic download
            LOGGER.info("yt-dlp not found. Starting automatic download...");
            LOGGER.info("This may take a few seconds depending on your internet connection...");
            LOGGER.info("Server startup will continue in the background...");
            
            // Download yt-dlp asynchronously to not block server startup
            org.stepan.audio_disc.download.YtDlpManager.downloadAndInstall().thenAccept(success -> {
                if (success) {
                    LOGGER.info("yt-dlp successfully installed automatically!");
                    LOGGER.info("YouTube functionality is now available");
                    LOGGER.info("Players can now use /audiodisc youtube <url> command");
                    
                    long size = org.stepan.audio_disc.download.YtDlpManager.getInstallationSize();
                    if (size > 0) {
                        LOGGER.info("Installation size: {}", formatBytes(size));
                    }
                } else {
                    LOGGER.warn("Failed to install yt-dlp automatically");
                    LOGGER.warn("YouTube functionality will not be available");
                    LOGGER.warn("You can try manual installation:");
                    LOGGER.warn("   - Use /audiodisc ytdlp install command (requires admin)");
                    LOGGER.warn("   - Or install yt-dlp manually on your system");
                    LOGGER.warn("   - Or disable auto-install in config: autoInstallYtDlp = false");
                }
            }).exceptionally(throwable -> {
                LOGGER.error("Error during yt-dlp installation", throwable);
                LOGGER.warn("YouTube functionality will not be available");
                LOGGER.warn("You can disable auto-install in config: autoInstallYtDlp = false");
                LOGGER.warn("Or try manual installation with /audiodisc ytdlp install");
                return null;
            });
            
        } catch (Exception e) {
            LOGGER.error("Error initializing yt-dlp", e);
            LOGGER.warn("YouTube functionality may not be available");
        }
    }
    
    /**
     * Initializes FFmpeg automatically on server start.
     */
    private static void initializeFFmpeg() {
        try {
            // Check if auto-install is enabled in config
            if (!config.isAutoInstallFFmpeg()) {
                LOGGER.info("Automatic FFmpeg installation is disabled in config");
                LOGGER.info("FFmpeg is needed for M4A/WebM conversion");
                LOGGER.info("Install FFmpeg manually for better format support");
                return;
            }
            
            LOGGER.info("Checking FFmpeg availability on server startup...");
            
            // Check if FFmpeg is already available
            if (org.stepan.audio_disc.download.FFmpegManager.isAvailable()) {
                String executablePath = org.stepan.audio_disc.download.FFmpegManager.getExecutablePath();
                LOGGER.info("FFmpeg is already available at: {}", executablePath);
                LOGGER.info("Audio conversion functionality is ready!");
                
                long size = org.stepan.audio_disc.download.FFmpegManager.getInstallationSize();
                if (size > 0) {
                    LOGGER.info("Installation size: {}", formatBytes(size));
                }
                return;
            }
            
            // If not available, start automatic download
            LOGGER.info("FFmpeg not found. Starting automatic download...");
            LOGGER.info("This may take a few minutes depending on your internet connection...");
            LOGGER.info("Server startup will continue in the background...");
            
            // Download FFmpeg asynchronously to not block server startup
            org.stepan.audio_disc.download.FFmpegManager.downloadAndInstall().thenAccept(success -> {
                if (success) {
                    LOGGER.info("FFmpeg successfully installed automatically!");
                    LOGGER.info("Audio conversion functionality is now available");
                    LOGGER.info("M4A and WebM files will now be converted automatically");
                    
                    long size = org.stepan.audio_disc.download.FFmpegManager.getInstallationSize();
                    if (size > 0) {
                        LOGGER.info("Installation size: {}", formatBytes(size));
                    }
                } else {
                    LOGGER.warn("Failed to install FFmpeg automatically");
                    LOGGER.warn("M4A/WebM conversion will not be available");
                    LOGGER.warn("You can try manual installation:");
                    LOGGER.warn("   - Download from https://ffmpeg.org/download.html");
                    LOGGER.warn("   - Or disable auto-install in config: autoInstallFFmpeg = false");
                }
            }).exceptionally(throwable -> {
                LOGGER.error("Error during FFmpeg installation", throwable);
                LOGGER.warn("Audio conversion functionality will not be available");
                LOGGER.warn("You can disable auto-install in config: autoInstallFFmpeg = false");
                LOGGER.warn("Or try manual installation from https://ffmpeg.org/download.html");
                return null;
            });
            
        } catch (Exception e) {
            LOGGER.error("Error initializing FFmpeg", e);
            LOGGER.warn("Audio conversion functionality may not be available");
        }
    }
    
    /**
     * Formats bytes to human-readable format.
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Tests if audio libraries are properly loaded.
     */
    private static void testAudioLibraries() {
        try {
            LOGGER.info("Testing audio library availability...");
            
            // List all available audio file readers (using reflection since getAudioFileReaders is private)
            boolean mp3Support = false;
            boolean oggSupport = false;
            
            try {
                java.lang.reflect.Method method = javax.sound.sampled.AudioSystem.class.getDeclaredMethod("getAudioFileReaders");
                method.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<javax.sound.sampled.spi.AudioFileReader> readers = 
                    (java.util.List<javax.sound.sampled.spi.AudioFileReader>) method.invoke(null);
                
                LOGGER.info("Found {} audio file readers:", readers.size());
                
                for (javax.sound.sampled.spi.AudioFileReader reader : readers) {
                    String className = reader.getClass().getName();
                    LOGGER.info("  - {}", className);
                    
                    if (className.contains("mpeg") || className.contains("mp3")) {
                        mp3Support = true;
                    }
                    if (className.contains("ogg") || className.contains("vorbis")) {
                        oggSupport = true;
                    }
                }
            } catch (Exception reflectionException) {
                LOGGER.warn("Could not list audio file readers using reflection: {}", reflectionException.getMessage());
                
                // Fallback: try to load specific classes to test support
                try {
                    Class.forName("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader");
                    mp3Support = true;
                    LOGGER.info("MP3 support detected via class loading");
                } catch (ClassNotFoundException e) {
                    LOGGER.info("MP3 support not detected");
                }
                
                // Skip OGG for now to avoid conflicts
                LOGGER.info("OGG support disabled to prevent conflicts");
            }
            
            LOGGER.info("Audio format support:");
            LOGGER.info("  MP3: {}", mp3Support ? "✓" : "✗");
            LOGGER.info("  OGG: {}", oggSupport ? "✓" : "✗");
            LOGGER.info("  WAV: ✓ (built-in)");
            
            if (!mp3Support) {
                LOGGER.warn("MP3 support not available! MP3 files may not play correctly.");
                LOGGER.warn("Make sure mp3spi library is properly included in the mod.");
            }
            
            if (!oggSupport) {
                LOGGER.warn("OGG support not available! OGG files may not play correctly.");
                LOGGER.warn("Make sure vorbisspi library is properly included in the mod.");
            }
            
        } catch (Exception e) {
            LOGGER.error("Error testing audio libraries: {}", e.getMessage());
        }
    }
}
