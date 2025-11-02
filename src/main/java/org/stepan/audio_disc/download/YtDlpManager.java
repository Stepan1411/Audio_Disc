package org.stepan.audio_disc.download;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

/**
 * Manages automatic download and installation of yt-dlp.
 */
public class YtDlpManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");
    
    // yt-dlp release URLs for different platforms
    private static final String WINDOWS_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
    private static final String LINUX_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";
    private static final String MACOS_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos";
    
    private static final Path YT_DLP_DIR = FabricLoader.getInstance().getGameDir().resolve("audiodisc").resolve("yt-dlp");
    private static Path ytDlpExecutable = null;
    
    /**
     * Checks if yt-dlp is available (either system-installed or mod-installed).
     */
    public static boolean isAvailable() {
        // First check if system yt-dlp is available
        if (isSystemYtDlpAvailable()) {
            return true;
        }
        
        // Then check if mod-installed yt-dlp is available
        return isModYtDlpAvailable();
    }
    
    /**
     * Gets the path to yt-dlp executable.
     */
    public static String getExecutablePath() {
        // Prefer system yt-dlp if available
        if (isSystemYtDlpAvailable()) {
            return "yt-dlp";
        }
        
        // Use mod-installed yt-dlp
        if (ytDlpExecutable != null && Files.exists(ytDlpExecutable)) {
            return ytDlpExecutable.toString();
        }
        
        return null;
    }
    
    /**
     * Downloads and installs yt-dlp automatically.
     */
    public static CompletableFuture<Boolean> downloadAndInstall() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Starting automatic yt-dlp download...");
                
                // Create directory if it doesn't exist
                if (!Files.exists(YT_DLP_DIR)) {
                    Files.createDirectories(YT_DLP_DIR);
                }
                
                // Determine platform and download URL
                String downloadUrl = getDownloadUrlForPlatform();
                String fileName = getExecutableNameForPlatform();
                
                if (downloadUrl == null || fileName == null) {
                    LOGGER.error("Unsupported platform for automatic yt-dlp download");
                    return false;
                }
                
                Path executable = YT_DLP_DIR.resolve(fileName);
                
                // Download yt-dlp
                LOGGER.info("Downloading yt-dlp from: {}", downloadUrl);
                if (!downloadFile(downloadUrl, executable)) {
                    return false;
                }
                
                // Make executable on Unix systems
                if (!isWindows()) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder("chmod", "+x", executable.toString());
                        Process process = pb.start();
                        process.waitFor();
                    } catch (Exception e) {
                        LOGGER.warn("Failed to make yt-dlp executable: {}", e.getMessage());
                    }
                }
                
                ytDlpExecutable = executable;
                LOGGER.info("Successfully downloaded and installed yt-dlp to: {}", executable);
                
                // Test the installation
                if (testYtDlp(executable.toString())) {
                    LOGGER.info("yt-dlp installation verified successfully");
                    return true;
                } else {
                    LOGGER.error("yt-dlp installation verification failed");
                    return false;
                }
                
            } catch (Exception e) {
                LOGGER.error("Failed to download and install yt-dlp", e);
                return false;
            }
        });
    }
    
    /**
     * Checks if system yt-dlp is available.
     */
    private static boolean isSystemYtDlpAvailable() {
        return testYtDlp("yt-dlp");
    }
    
    /**
     * Checks if mod-installed yt-dlp is available.
     */
    private static boolean isModYtDlpAvailable() {
        if (ytDlpExecutable == null) {
            // Try to find existing installation
            String fileName = getExecutableNameForPlatform();
            if (fileName != null) {
                Path executable = YT_DLP_DIR.resolve(fileName);
                if (Files.exists(executable)) {
                    ytDlpExecutable = executable;
                    return testYtDlp(executable.toString());
                }
            }
        }
        
        return ytDlpExecutable != null && Files.exists(ytDlpExecutable) && testYtDlp(ytDlpExecutable.toString());
    }
    
    /**
     * Tests if yt-dlp executable works.
     */
    private static boolean testYtDlp(String executable) {
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Downloads a file from URL to destination.
     */
    private static boolean downloadFile(String urlString, Path destination) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", "AudioDisc-Mod/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOGGER.error("Failed to download yt-dlp: HTTP {}", responseCode);
                return false;
            }
            
            long fileSize = connection.getContentLengthLong();
            LOGGER.info("Downloading yt-dlp ({} MB)...", fileSize > 0 ? String.format("%.1f", fileSize / 1024.0 / 1024.0) : "unknown size");
            
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            
            LOGGER.info("Download completed: {}", destination);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to download yt-dlp", e);
            return false;
        }
    }
    
    /**
     * Gets the download URL for the current platform.
     */
    private static String getDownloadUrlForPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return WINDOWS_URL;
        } else if (os.contains("mac")) {
            return MACOS_URL;
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return LINUX_URL;
        }
        
        return null;
    }
    
    /**
     * Gets the executable name for the current platform.
     */
    private static String getExecutableNameForPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return "yt-dlp.exe";
        } else {
            return "yt-dlp";
        }
    }
    
    /**
     * Checks if running on Windows.
     */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
    
    /**
     * Gets the size of the yt-dlp installation.
     */
    public static long getInstallationSize() {
        if (ytDlpExecutable != null && Files.exists(ytDlpExecutable)) {
            try {
                return Files.size(ytDlpExecutable);
            } catch (IOException e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Removes the mod-installed yt-dlp.
     */
    public static boolean uninstall() {
        try {
            if (ytDlpExecutable != null && Files.exists(ytDlpExecutable)) {
                Files.delete(ytDlpExecutable);
                ytDlpExecutable = null;
                LOGGER.info("Uninstalled mod-installed yt-dlp");
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("Failed to uninstall yt-dlp", e);
            return false;
        }
    }
}