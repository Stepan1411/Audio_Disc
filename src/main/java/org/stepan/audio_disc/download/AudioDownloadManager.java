package org.stepan.audio_disc.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stepan.audio_disc.model.DownloadStatus;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class AudioDownloadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AudioDisc");
    
    private final ExecutorService downloadExecutor;
    private final Map<UUID, DownloadTask> activeDownloads;
    private final long maxFileSize;
    private final int timeoutSeconds;
    private final int maxConcurrentDownloads;
    private final Semaphore downloadSemaphore;

    public AudioDownloadManager(long maxFileSize, int timeoutSeconds, int maxConcurrentDownloads) {
        this.downloadExecutor = Executors.newFixedThreadPool(maxConcurrentDownloads);
        this.activeDownloads = new ConcurrentHashMap<>();
        this.maxFileSize = maxFileSize;
        this.timeoutSeconds = timeoutSeconds;
        this.maxConcurrentDownloads = maxConcurrentDownloads;
        this.downloadSemaphore = new Semaphore(maxConcurrentDownloads);
    }

    /**
     * Initiates an asynchronous audio download.
     *
     * @param url The URL to download from
     * @param playerId The UUID of the player initiating the download
     * @param progressCallback Callback for progress updates (0-100)
     * @return CompletableFuture containing the downloaded audio data
     */
    public CompletableFuture<byte[]> downloadAudio(String url, UUID playerId, Consumer<Integer> progressCallback) {
        // Validate URL first
        try {
            validateUrl(url);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }

        // Check if we can start a new download
        if (!downloadSemaphore.tryAcquire()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Server download limit reached. Please try again later.")
            );
        }

        UUID taskId = UUID.randomUUID();
        DownloadTask task = new DownloadTask(taskId, url, playerId, progressCallback, maxFileSize, timeoutSeconds);
        activeDownloads.put(taskId, task);

        LOGGER.info("Starting download for player {} from URL: {}", playerId, url);

        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] data = task.execute();
                LOGGER.info("Download completed for player {}: {} bytes", playerId, data.length);
                return data;
            } catch (Exception e) {
                LOGGER.error("Download failed for player {}: {}", playerId, e.getMessage());
                throw new CompletionException(e);
            } finally {
                activeDownloads.remove(taskId);
                downloadSemaphore.release();
            }
        }, downloadExecutor);
    }

    /**
     * Reports download progress for a specific task.
     *
     * @param downloadId The UUID of the download task
     * @param percentage The progress percentage (0-100)
     */
    public void reportProgress(UUID downloadId, int percentage) {
        DownloadTask task = activeDownloads.get(downloadId);
        if (task != null) {
            LOGGER.debug("Download {} progress: {}%", downloadId, percentage);
        }
    }

    /**
     * Cancels an active download.
     *
     * @param downloadId The UUID of the download task to cancel
     */
    public void cancelDownload(UUID downloadId) {
        DownloadTask task = activeDownloads.get(downloadId);
        if (task != null) {
            task.cancel();
            activeDownloads.remove(downloadId);
            LOGGER.info("Download {} cancelled", downloadId);
        }
    }

    /**
     * Gets the status of an active download.
     *
     * @param downloadId The UUID of the download task
     * @return The download status, or null if not found
     */
    public DownloadStatus getDownloadStatus(UUID downloadId) {
        DownloadTask task = activeDownloads.get(downloadId);
        return task != null ? task.getStatus() : null;
    }

    /**
     * Validates a URL for security and format.
     *
     * @param url The URL to validate
     * @throws IllegalArgumentException if the URL is invalid or insecure
     */
    private void validateUrl(String url) throws IllegalArgumentException {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            
            // Only allow HTTP and HTTPS
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Only HTTP and HTTPS protocols are allowed");
            }

            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("Invalid URL: no host specified");
            }

            // Block local and private IP ranges
            try {
                InetAddress address = InetAddress.getByName(host);
                if (isBlockedAddress(address)) {
                    throw new IllegalArgumentException("Access to local/private IP addresses is not allowed");
                }
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Cannot resolve host: " + host);
            }

        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("Invalid URL format: " + e.getMessage());
        }
    }

    /**
     * Checks if an IP address is in a blocked range.
     *
     * @param address The IP address to check
     * @return true if the address should be blocked
     */
    private boolean isBlockedAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        
        // Check for loopback (127.0.0.0/8)
        if (bytes[0] == 127) {
            return true;
        }
        
        // Check for private networks (10.0.0.0/8)
        if (bytes[0] == 10) {
            return true;
        }
        
        // Check for private networks (172.16.0.0/12)
        if (bytes[0] == (byte) 172 && (bytes[1] & 0xF0) == 16) {
            return true;
        }
        
        // Check for private networks (192.168.0.0/16)
        if (bytes[0] == (byte) 192 && bytes[1] == (byte) 168) {
            return true;
        }
        
        // Check for link-local (169.254.0.0/16)
        if (bytes[0] == (byte) 169 && bytes[1] == (byte) 254) {
            return true;
        }
        
        return false;
    }

    /**
     * Gets the number of active downloads.
     *
     * @return The count of active downloads
     */
    public int getActiveDownloadCount() {
        return activeDownloads.size();
    }

    /**
     * Shuts down the download manager and cancels all active downloads.
     */
    public void shutdown() {
        LOGGER.info("Shutting down AudioDownloadManager");
        
        // Cancel all active downloads
        activeDownloads.values().forEach(DownloadTask::cancel);
        activeDownloads.clear();
        
        // Shutdown executor
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
