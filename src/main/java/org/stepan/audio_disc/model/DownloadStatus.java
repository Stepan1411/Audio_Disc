package org.stepan.audio_disc.model;

/**
 * Represents the status of an audio download operation.
 */
public enum DownloadStatus {
    /**
     * Download is queued but not yet started.
     */
    PENDING,
    
    /**
     * Download is currently in progress.
     */
    DOWNLOADING,
    
    /**
     * Download completed, processing the audio file.
     */
    PROCESSING,
    
    /**
     * Download and processing completed successfully.
     */
    COMPLETE,
    
    /**
     * Download or processing failed.
     */
    FAILED,
    
    /**
     * Download was cancelled by user or system.
     */
    CANCELLED
}
