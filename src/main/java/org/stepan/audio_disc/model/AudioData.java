package org.stepan.audio_disc.model;

/**
 * Represents audio data with associated metadata and tracking information.
 */
public class AudioData {
    private final String id;
    private final byte[] data;
    private final AudioMetadata metadata;
    private final String uploadedBy;
    private final long uploadTime;

    /**
     * Creates a new AudioData instance.
     * 
     * @param id The unique identifier for this audio
     * @param data The raw audio data bytes
     * @param metadata The audio metadata
     * @param uploadedBy The username of the player who uploaded this audio
     * @param uploadTime The timestamp when this audio was uploaded (milliseconds since epoch)
     */
    public AudioData(String id, byte[] data, AudioMetadata metadata, String uploadedBy, long uploadTime) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID cannot be null or blank");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("Metadata cannot be null");
        }
        if (uploadedBy == null || uploadedBy.isBlank()) {
            throw new IllegalArgumentException("UploadedBy cannot be null or blank");
        }
        if (uploadTime < 0) {
            throw new IllegalArgumentException("UploadTime cannot be negative");
        }
        
        this.id = id;
        this.data = data.clone(); // Defensive copy
        this.metadata = metadata;
        this.uploadedBy = uploadedBy;
        this.uploadTime = uploadTime;
    }

    public String getId() {
        return id;
    }

    public byte[] getData() {
        return data.clone(); // Defensive copy
    }

    public AudioMetadata getMetadata() {
        return metadata;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public long getUploadTime() {
        return uploadTime;
    }

    // Record-style accessor methods for compatibility
    public String id() {
        return id;
    }

    public byte[] data() {
        return getData();
    }

    public AudioMetadata metadata() {
        return metadata;
    }

    public String uploadedBy() {
        return uploadedBy;
    }

    public long uploadTime() {
        return uploadTime;
    }
}
