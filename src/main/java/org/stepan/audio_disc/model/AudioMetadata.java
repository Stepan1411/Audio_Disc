package org.stepan.audio_disc.model;

/**
 * Represents metadata for an audio file.
 * 
 * @param format The audio format (e.g., "mp3", "ogg", "wav")
 * @param duration The duration of the audio in milliseconds
 * @param bitrate The bitrate in bits per second
 * @param sampleRate The sample rate in Hz
 * @param title The title of the audio file
 */
public record AudioMetadata(
    String format,
    long duration,
    int bitrate,
    int sampleRate,
    String title
) {
    /**
     * Creates an AudioMetadata instance with validation.
     * 
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public AudioMetadata {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Format cannot be null or blank");
        }
        if (duration < 0) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
        if (bitrate < 0) {
            throw new IllegalArgumentException("Bitrate cannot be negative");
        }
        if (sampleRate < 0) {
            throw new IllegalArgumentException("Sample rate cannot be negative");
        }
        if (title == null) {
            throw new IllegalArgumentException("Title cannot be null");
        }
    }
}
