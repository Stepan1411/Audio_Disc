package org.stepan.audio_disc.exception;

/**
 * Base exception for Audio Disc mod errors.
 */
public class AudioDiscException extends Exception {
    private final ErrorType type;
    private final String userMessage;

    public AudioDiscException(ErrorType type, String userMessage) {
        super(userMessage);
        this.type = type;
        this.userMessage = userMessage;
    }

    public AudioDiscException(ErrorType type, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.type = type;
        this.userMessage = userMessage;
    }

    /**
     * Gets the error type.
     * 
     * @return The error type
     */
    public ErrorType getType() {
        return type;
    }

    /**
     * Gets the user-friendly error message.
     * 
     * @return The user message
     */
    public String getUserMessage() {
        return userMessage;
    }

    /**
     * Types of errors that can occur in the Audio Disc system.
     */
    public enum ErrorType {
        /**
         * The provided URL is invalid or malformed.
         */
        INVALID_URL("Invalid URL"),
        
        /**
         * The download failed due to network issues or unreachable URL.
         */
        DOWNLOAD_FAILED("Download failed"),
        
        /**
         * The audio file format is not supported.
         */
        UNSUPPORTED_FORMAT("Unsupported format"),
        
        /**
         * The file size exceeds the maximum allowed size.
         */
        FILE_TOO_LARGE("File too large"),
        
        /**
         * The player is not holding a music disc.
         */
        NO_DISC_IN_HAND("No disc in hand"),
        
        /**
         * An error occurred while storing or retrieving audio data.
         */
        STORAGE_ERROR("Storage error"),
        
        /**
         * An error occurred during audio playback.
         */
        PLAYBACK_ERROR("Playback error");

        private final String displayName;

        ErrorType(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Gets the display name of the error type.
         * 
         * @return The display name
         */
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Creates an INVALID_URL exception.
     * 
     * @param message The error message
     * @return The exception
     */
    public static AudioDiscException invalidUrl(String message) {
        return new AudioDiscException(ErrorType.INVALID_URL, message);
    }

    /**
     * Creates a DOWNLOAD_FAILED exception.
     * 
     * @param message The error message
     * @param cause The underlying cause
     * @return The exception
     */
    public static AudioDiscException downloadFailed(String message, Throwable cause) {
        return new AudioDiscException(ErrorType.DOWNLOAD_FAILED, message, cause);
    }

    /**
     * Creates an UNSUPPORTED_FORMAT exception.
     * 
     * @param message The error message
     * @return The exception
     */
    public static AudioDiscException unsupportedFormat(String message) {
        return new AudioDiscException(ErrorType.UNSUPPORTED_FORMAT, message);
    }

    /**
     * Creates a FILE_TOO_LARGE exception.
     * 
     * @param message The error message
     * @return The exception
     */
    public static AudioDiscException fileTooLarge(String message) {
        return new AudioDiscException(ErrorType.FILE_TOO_LARGE, message);
    }

    /**
     * Creates a NO_DISC_IN_HAND exception.
     * 
     * @param message The error message
     * @return The exception
     */
    public static AudioDiscException noDiscInHand(String message) {
        return new AudioDiscException(ErrorType.NO_DISC_IN_HAND, message);
    }

    /**
     * Creates a STORAGE_ERROR exception.
     * 
     * @param message The error message
     * @param cause The underlying cause
     * @return The exception
     */
    public static AudioDiscException storageError(String message, Throwable cause) {
        return new AudioDiscException(ErrorType.STORAGE_ERROR, message, cause);
    }

    /**
     * Creates a PLAYBACK_ERROR exception.
     * 
     * @param message The error message
     * @param cause The underlying cause
     * @return The exception
     */
    public static AudioDiscException playbackError(String message, Throwable cause) {
        return new AudioDiscException(ErrorType.PLAYBACK_ERROR, message, cause);
    }
}
