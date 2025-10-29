package org.stepan.audio_disc.download;

import org.stepan.audio_disc.model.DownloadStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.function.Consumer;

public class DownloadTask {
    private final UUID taskId;
    private final String url;
    private final UUID playerId;
    private volatile int progress;
    private volatile DownloadStatus status;
    private volatile boolean cancelled;
    private final Consumer<Integer> progressCallback;
    private final long maxFileSize;
    private final int timeoutSeconds;

    public DownloadTask(UUID taskId, String url, UUID playerId, 
                       Consumer<Integer> progressCallback, long maxFileSize, int timeoutSeconds) {
        this.taskId = taskId;
        this.url = url;
        this.playerId = playerId;
        this.progress = 0;
        this.status = DownloadStatus.PENDING;
        this.cancelled = false;
        this.progressCallback = progressCallback;
        this.maxFileSize = maxFileSize;
        this.timeoutSeconds = timeoutSeconds;
    }

    public byte[] execute() throws IOException {
        if (cancelled) {
            status = DownloadStatus.CANCELLED;
            throw new IOException("Download cancelled");
        }

        status = DownloadStatus.DOWNLOADING;
        
        URL downloadUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setRequestProperty("User-Agent", "AudioDisc-Minecraft-Mod/1.0");

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }

            // Validate content type
            String contentType = connection.getContentType();
            if (contentType != null) {
                contentType = contentType.toLowerCase();
                boolean validContentType = contentType.contains("audio/") || 
                                          contentType.contains("application/octet-stream") ||
                                          contentType.contains("application/ogg") ||
                                          contentType.contains("application/binary");
                if (!validContentType) {
                    throw new IOException("Invalid content type: " + contentType + ". Expected audio file.");
                }
            }

            long contentLength = connection.getContentLengthLong();
            if (contentLength > maxFileSize) {
                throw new IOException("File too large: " + contentLength + " bytes (max: " + maxFileSize + ")");
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            InputStream inputStream = connection.getInputStream();
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            int lastReportedProgress = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (cancelled) {
                    status = DownloadStatus.CANCELLED;
                    throw new IOException("Download cancelled");
                }

                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                // Check size limit during download
                if (totalBytesRead > maxFileSize) {
                    throw new IOException("File size exceeded during download");
                }

                // Calculate and report progress
                if (contentLength > 0) {
                    int currentProgress = (int) ((totalBytesRead * 100) / contentLength);
                    if (currentProgress >= lastReportedProgress + 25) {
                        progress = currentProgress;
                        lastReportedProgress = currentProgress;
                        if (progressCallback != null) {
                            progressCallback.accept(currentProgress);
                        }
                    }
                }
            }

            progress = 100;
            if (progressCallback != null) {
                progressCallback.accept(100);
            }

            status = DownloadStatus.COMPLETE;
            return outputStream.toByteArray();

        } finally {
            connection.disconnect();
        }
    }

    public void cancel() {
        this.cancelled = true;
        this.status = DownloadStatus.CANCELLED;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getUrl() {
        return url;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getProgress() {
        return progress;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
