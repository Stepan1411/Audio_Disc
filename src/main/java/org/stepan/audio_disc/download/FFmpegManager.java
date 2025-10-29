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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages automatic download and installation of FFmpeg.
 */
public class FFmpegManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("FFmpegManager");
    
    // FFmpeg release URLs for different platforms
    private static final String WINDOWS_URL = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
    private static final String LINUX_URL = "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz";
    
    private static final Path FFMPEG_DIR = FabricLoader.getInstance().getGameDir().resolve("audiodisc").resolve("ffmpeg");
    private static Path ffmpegExecutable = null;
    
    /**
     * Checks if FFmpeg is available (either system-installed or mod-installed).
     */
    public static boolean isAvailable() {
        // First check if system FFmpeg is available
        if (isSystemFFmpegAvailable()) {
            return true;
        }
        
        // Then check if mod-installed FFmpeg is available
        return isModFFmpegAvailable();
    }
    
    /**
     * Gets the path to FFmpeg executable.
     */
    public static String getExecutablePath() {
        // Prefer system FFmpeg if available
        if (isSystemFFmpegAvailable()) {
            return "ffmpeg";
        }
        
        // Use mod-installed FFmpeg
        if (ffmpegExecutable != null && Files.exists(ffmpegExecutable)) {
            return ffmpegExecutable.toString();
        }
        
        return null;
    }
    
    /**
     * Downloads and installs FFmpeg automatically.
     */
    public static CompletableFuture<Boolean> downloadAndInstall() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Starting automatic FFmpeg download...");
                
                // Create directory if it doesn't exist
                if (!Files.exists(FFMPEG_DIR)) {
                    Files.createDirectories(FFMPEG_DIR);
                }
                
                // Determine platform and download URL
                String downloadUrl = getDownloadUrlForPlatform();
                
                if (downloadUrl == null) {
                    LOGGER.error("Unsupported platform for automatic FFmpeg download");
                    return false;
                }
                
                // Only support Windows for now (Linux FFmpeg installation is more complex)
                if (!isWindows()) {
                    LOGGER.info("Automatic FFmpeg installation only supported on Windows");
                    LOGGER.info("Please install FFmpeg manually on Linux/macOS");
                    return false;
                }
                
                // Download FFmpeg
                LOGGER.info("Downloading FFmpeg from: {}", downloadUrl);
                Path tempZip = FFMPEG_DIR.resolve("ffmpeg-temp.zip");
                
                if (!downloadFile(downloadUrl, tempZip)) {
                    return false;
                }
                
                // Extract FFmpeg
                LOGGER.info("Extracting FFmpeg...");
                if (!extractFFmpeg(tempZip)) {
                    return false;
                }
                
                // Clean up temp file
                try {
                    Files.deleteIfExists(tempZip);
                } catch (Exception e) {
                    LOGGER.warn("Failed to delete temp file: {}", e.getMessage());
                }
                
                // Find FFmpeg executable
                Path executable = findFFmpegExecutable();
                if (executable == null) {
                    LOGGER.error("FFmpeg executable not found after extraction");
                    return false;
                }
                
                ffmpegExecutable = executable;
                LOGGER.info("Successfully downloaded and installed FFmpeg to: {}", executable);
                
                // Test the installation
                if (testFFmpeg(executable.toString())) {
                    LOGGER.info("FFmpeg installation verified successfully");
                    return true;
                } else {
                    LOGGER.error("FFmpeg installation verification failed");
                    return false;
                }
                
            } catch (Exception e) {
                LOGGER.error("Failed to download and install FFmpeg", e);
                return false;
            }
        });
    }
    
    /**
     * Checks if system FFmpeg is available.
     */
    private static boolean isSystemFFmpegAvailable() {
        return testFFmpeg("ffmpeg");
    }
    
    /**
     * Checks if mod-installed FFmpeg is available.
     */
    private static boolean isModFFmpegAvailable() {
        if (ffmpegExecutable == null) {
            // Try to find existing installation
            Path executable = findFFmpegExecutable();
            if (executable != null && Files.exists(executable)) {
                ffmpegExecutable = executable;
                return testFFmpeg(executable.toString());
            }
        }
        
        return ffmpegExecutable != null && Files.exists(ffmpegExecutable) && testFFmpeg(ffmpegExecutable.toString());
    }
    
    /**
     * Tests if FFmpeg executable works.
     */
    private static boolean testFFmpeg(String executable) {
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "-version");
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
            connection.setReadTimeout(300000); // 5 minutes for large files
            connection.setRequestProperty("User-Agent", "AudioDisc-Mod/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LOGGER.error("Failed to download FFmpeg: HTTP {}", responseCode);
                return false;
            }
            
            long fileSize = connection.getContentLengthLong();
            LOGGER.info("Downloading FFmpeg ({} MB)...", fileSize > 0 ? String.format("%.1f", fileSize / 1024.0 / 1024.0) : "unknown size");
            
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            
            LOGGER.info("Download completed: {}", destination);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to download FFmpeg", e);
            return false;
        }
    }
    
    /**
     * Extracts FFmpeg from zip file (Windows only).
     */
    private static boolean extractFFmpeg(Path zipFile) {
        try {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    
                    // Look for ffmpeg.exe in any subdirectory
                    if (entryName.endsWith("ffmpeg.exe") && !entry.isDirectory()) {
                        Path outputPath = FFMPEG_DIR.resolve("ffmpeg.exe");
                        
                        LOGGER.info("Extracting FFmpeg executable: {} -> {}", entryName, outputPath);
                        
                        try (OutputStream out = Files.newOutputStream(outputPath)) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = zis.read(buffer)) != -1) {
                                out.write(buffer, 0, length);
                            }
                        }
                        
                        LOGGER.info("FFmpeg extracted successfully");
                        return true;
                    }
                    zis.closeEntry();
                }
            }
            
            LOGGER.error("ffmpeg.exe not found in downloaded archive");
            return false;
            
        } catch (Exception e) {
            LOGGER.error("Failed to extract FFmpeg", e);
            return false;
        }
    }
    
    /**
     * Finds FFmpeg executable in the installation directory.
     */
    private static Path findFFmpegExecutable() {
        try {
            if (isWindows()) {
                Path executable = FFMPEG_DIR.resolve("ffmpeg.exe");
                if (Files.exists(executable)) {
                    return executable;
                }
            } else {
                Path executable = FFMPEG_DIR.resolve("ffmpeg");
                if (Files.exists(executable)) {
                    return executable;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error finding FFmpeg executable: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Gets the download URL for the current platform.
     */
    private static String getDownloadUrlForPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            return WINDOWS_URL;
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return LINUX_URL; // Note: Linux extraction not implemented yet
        }
        
        return null;
    }
    
    /**
     * Checks if running on Windows.
     */
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
    
    /**
     * Gets the size of the FFmpeg installation.
     */
    public static long getInstallationSize() {
        if (ffmpegExecutable != null && Files.exists(ffmpegExecutable)) {
            try {
                return Files.size(ffmpegExecutable);
            } catch (IOException e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Removes the mod-installed FFmpeg.
     */
    public static boolean uninstall() {
        try {
            if (ffmpegExecutable != null && Files.exists(ffmpegExecutable)) {
                Files.delete(ffmpegExecutable);
                ffmpegExecutable = null;
                LOGGER.info("Uninstalled mod-installed FFmpeg");
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("Failed to uninstall FFmpeg", e);
            return false;
        }
    }
}